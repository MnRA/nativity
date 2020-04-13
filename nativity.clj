#!/usr/bin/env bb

(ns nativity
  (:require [clojure.string :refer [join split split-lines] :as str]
            [clojure.java.io :refer [make-parents delete-file]]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [java.lang ProcessBuilder$Redirect]))


(defn dependency-parser [input-string] (map keyword (split input-string #",")))
(defn slice-parser [input-string]
  (let [[start end] (map #(Integer/parseInt %) (split input-string #"-"))]
  [(dec start) end]
))

(def cli-options
  [["-d" "--deps LIST" "Specify the dependency list for implicit mode ( comma seperated values, example: \"io,str,json,edn\")"
    :default []
    :parse-fn dependency-parser]
   ["-n" "--name FILENAME" "Override default file name for the binary (will default to the input file name)"]
   ["-c" "--clean" "Clean up the src and deps.edn files"]
   ["-m" "--mode MODE" "Choose the processing mode. Currently available: implicit, untouched, directed"
    :default "untouched"]
   ["" "--no-compile" "Don't run binary compilation step"
    :default false]
   ["" "--namespace NAMESPACE-NAME" "If your main function is in a namespace different from your file name you can override with this. It will also use this to name the namespace in implicit mode"]
   ["-s" "--slice RANGE" "Determine the slice you want to inject into main (Only directed mode)"
    :default [0, 0]
    :parse-fn slice-parser]
   ])

(def command-line-input (parse-opts *command-line-args* cli-options))
(def options (:options command-line-input))
(println command-line-input)
(def file-path (first (:arguments command-line-input)))
(def file (-> file-path (split #"/") last))
(def file-name (-> file (split #"\.") first))
(def name-space (or (:namespace options) file-name))
(def native-image-name (or (:name options) file-name))
(def dependency-keys (:deps options))


;shamelessly copied
(defn- shell-command
  ([args] (shell-command args nil))
  ([args {:keys [:throw?]
          :or {throw? true}}]
   (let [pb (let [pb (ProcessBuilder. ^java.util.List args)]
              (doto pb
                (.redirectInput ProcessBuilder$Redirect/INHERIT)
                (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                (.redirectError ProcessBuilder$Redirect/INHERIT)))
         proc (.start pb)
         exit-code (.waitFor proc)]
     (when (and throw?
                (not (zero? exit-code)))
       (throw (ex-info "Got non-zero exit code" {:status exit-code})))
     {:exit exit-code})))

(def deps-file
 {:deps {'cheshire {:mvn/version "5.10.0"}
       'org.clojure/tools.cli {:mvn/version "1.0.194"}
       'org.clojure/clojure {:mvn/version "1.10.2-alpha1"}
       'org.clojure/core.async {:mvn/version "1.1.587"}
       'com.cognitect/transit-clj {:mvn/version "1.0.324"}
       'bencode {:mvn/version "0.2.5"}
       'org.clojure/data.csv {:mvn/version "1.0.0"}}
 :aliases {:native-image
          {:main-opts [(str "-m clj.native-image " name-space)
                       "--initialize-at-build-time "
                       ;; optional native image name override
                       (str "-H:Name=" native-image-name)
                       "-H:+ReportExceptionStackTraces"]
           :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
           :extra-deps
           {'clj.native-image
            {:git/url "https://github.com/taylorwood/clj.native-image.git"
             :sha "7708e7fd4572459c81f6a6b8e44c96f41cdd92d4"}}}}})


(def dependency-map
  '{ :str [clojure.string :as str]
     :set [clojure.set :as set]
     :edn [clojure.edn :as edn]
     :shell [clojure.java.shell :as shell]
     :io [clojure.java.io :as io]
     :async [clojure.core.async :as async]
     :stacktrace [clojure.stacktrace]
     :test [clojure.test]
     :pprint [clojure.pprint :as pprint]
     :cli [clojure.tools.cli :as cli]
     :csv [clojure.data.csv :as csv]
     :json [cheshire.core :as json]
     :transit [cognitect.transit :as transit]
     :bencode [bencode.core :as bencode]})


(defn specific-dependencies [keylist]
 (cond
    (some #{:all} keylist) (conj (vals dependency-map) :require)
    (empty? keylist) nil
    :else (conj (vals (select-keys dependency-map keylist)) :require)))

(def compile-to-native-command ["clj" "-A:native-image" "--no-fallback"] )


(defn generate-with [src-gen-fn]
  (println "reading file")
  (def main-file (slurp file-path))

  (def src-output-path (str "src/" name-space ".clj"))

  (println "making directory")
  (make-parents src-output-path)

  (println "making modified script-file")
  (src-gen-fn)

  (println "making deps file")
  (spit "deps.edn" (str deps-file))

  (if-not (:no-compile options)
    (do
      (println "compiling native binary")
      (shell-command compile-to-native-command))))


(defn implicit-generation []
  (spit src-output-path
    (str
      (apply list (remove nil?
        (list 'ns (symbol name-space)
          '(:gen-class)
           (specific-dependencies dependency-keys))))
      "(defn -main [& *command-line-args*] " main-file " )"
  )))

(defn untouched-generation []
  (spit src-output-path main-file))

(defn directed-generation []
  (let [lines-of-main-file (split-lines main-file)
        [start-line, stop-line] (:slice options)
        inject-to-main (take (- stop-line start-line) (drop start-line lines-of-main-file))
        initial-part (take start-line lines-of-main-file)
        final-part (drop stop-line lines-of-main-file)
        entry-point (flatten ["(defn -main [& *command-line-args*] " inject-to-main ")"])]
   (spit src-output-path (join "\n" (flatten [initial-part entry-point final-part]))))
  )

(defn main []
  (case (:mode options)
    "implicit" (generate-with implicit-generation)
    "untouched" (generate-with untouched-generation)
    "directed" (generate-with directed-generation)
    (generate-with untouched-generation))

  (if (:clean options)
   (do
     (println "removing source file")
     (delete-file src-output-path)
     (println "removing deps.edn file")
     (delete-file "deps.edn"))))

(main)
