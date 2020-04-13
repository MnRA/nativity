#!/usr/bin/env bb

(ns nativity
  (:require [clojure.string :refer [split] :as str]
            [clojure.java.io :refer [make-parents delete-file]]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [java.lang ProcessBuilder$Redirect]))


(defn dependency-parser [input-string] (map keyword (split input-string #",")))
(def cli-options
  [["-d" "--deps LIST" "dependency list ( comma seperated values, example: \"io,str,json,edn\")"
    :default []
    :parse-fn dependency-parser]
   ["-n" "--name FILENAME" "override default file name for the binary (will default to the input file name )"
    :default nil]
    ["-k" "-keep-files" "keep the generated intermediate files"]
   ])

(def command-line-input (parse-opts *command-line-args* cli-options))
(def options (:options command-line-input))

(def file-path (first (:arguments command-line-input)))
(def file (-> file-path (split #"/") last))
(def file-name (-> file (split #"\.") first))
(def name-space file-name)
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
                       "--initialize-at-build-time"
                       ;; optional native image name override
                       (str "-H:Name=" native-image-name)]
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

(println "reading file")
(def main-file (slurp file-path))

(def src-output-path (str "src/" file-name ".clj"))

(println "making directory")
(make-parents src-output-path)

(println "making modified script-file")

(defn specific-require-generation []
  (spit src-output-path
    (str
      (apply list (remove nil?
        (list 'ns (symbol name-space)
          '(:gen-class)
           (specific-dependencies dependency-keys))))
      "(defn -main [& *command-line-args*] " main-file " )"
  ))

  (println "making deps file")
  (spit "deps.edn" (str deps-file))

  (println "compiling native image")
  (shell-command compile-to-native-command)
)

(defn main []
  (specific-require-generation)
  (if-not (:keep-files options)
   (do
     (println "removing source file")
     (delete-file src-output-path)
     (println "removing deps.edn file")
     (delete-file "deps.edn"))))

(main)
