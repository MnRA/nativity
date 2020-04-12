#!/usr/bin/env bb

; (def cli-options
;   [["-o" "--object RANGE" "object range ( min-max, example: 1-40)"
;     :default (range 1 40)
;     :parse-fn range-parser]
;    ; ["-f" "--frame RANGE" "frame range ( min-max, example: 1-40)"
;    ;  :default (range 1 901)
;    ;  :parse-fn range-parser]
;    ; ["-n" "--name FILENAME" "override default file-name"
;    ;  :default "foo"]
;    ])
;
; (def options (:options (clojure.tools.cli/parse-opts *command-line-args* cli-options)))

(def file-path (first *command-line-args*))
(def file (-> file-path (str/split #"/") last))
(def file-name (-> file (str/split #"\.") first))
(def name-space file-name)
(def native-image-name file-name)

(println "reading file")
(def main-file (slurp file))

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

(def compile-to-native-command "clj -A:native-image")
(def src-output-path (str "src/" file-name ".clj"))

(println "making directory")
(io/make-parents src-output-path)

(println "making modified script-file")
(spit src-output-path
  (str
    (conj
        '((:gen-class)
          (:require [clojure.string :as str]
              [clojure.set :as set]
              [clojure.edn :as edn]
              [clojure.java.shell :as shell]
              [clojure.java.io :as io]
              [clojure.core.async :as async]
              [clojure.stacktrace]
              [clojure.test]
              [clojure.pprint :as pprint]
              [clojure.tools.cli :as cli]
              [clojure.data.csv :as csv]
              [cheshire.core :as json]
              [cognitect.transit :as transit]
              [bencode.core :as bencode])) (symbol name-space) 'ns)
   "(defn -main [& *command-line-args*] " main-file " )"
))

(println "making deps file")
(spit "deps.edn" (str deps-file))

(println "compiling native image")
(shell/sh "clj" "-A:native-image")
