# nativity

A small script for turning babashka scripts into native binaries

## Requirements

   - JVM (I think?)
   - babashka
   - GraalVM

## Usage

  From the nativity directory: `bb nativity.clj path/to/script`
  or even just `./nativity.clj path/to/script`

### Modes
you can specify which mode to run on by using the `./nativity.clj path/to/script -m implicit` it will default to "untouched"

##### Untouched
Untouched will leave the file completely alone, make a copy into the src directory  and create the deps.edn file
for example:

``` clojure
#!/usr/bin/env bb
(ns which
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn where [executable]
  (let [path (System/getenv "PATH")
        paths (.split path (System/getProperty "path.separator"))]
    (loop [paths paths]
      (when-first [p paths]
        (let [f (io/file p executable)]
          (if (and (.isFile f)
                   (.canExecute f))
            (.getCanonicalPath f)
            (recur (rest paths))))))))

(defn -main [& args]
  (when-first [executable args]
    (println (where executable))))

(when (find-ns 'babashka.classpath) ;; we're running as a script
  (apply -main *command-line-args*))
```
This can compile as is because it has:
  * Ns form with :gen-class and explicit requires (`(ns which
    (:require [clojure.java.io :as io])
    (:gen-class))`)
  * an entry point (`defn -main [& args]`)
  * no in-line require forms (`(require ....)`)


##### Implicit
This mode is for when you made a script using the implicit requires that babashka has for one-liners.
Be warned that this mode is very inefficient in so many ways.
for example:
``` clojure
#!/usr/bin/env bb
(defn where [executable]
  (let [path (System/getenv "PATH")
        paths (.split path (System/getProperty "path.separator"))]
    (loop [paths paths]
      (when-first [p paths]
        (let [f (io/file p executable)]
          (if (and (.isFile f)
                   (.canExecute f))
            (.getCanonicalPath f)
            (recur (rest paths))))))))
(when-let [executable (first *command-line-args*)]
  (println (where executable)))
```
This one will require implicit mode and so it should be run `./nativity which.clj -m implicit -d io` (look below to understand the `-d` flag)

### Other Options

#### Import require specific things for implicit mode
You can specify which of the built in bb dependencies to require by using the `./nativity.clj path/to/script -d io,str,json`.
If you use `-d all` it will include all of them.

#### Rename binary
if you want your binary to have a different name:
`./nativity.clj path/to/script -n name-of-binary`

#### Clean files
With this flag you can make it automatically remove the generated src and deps.edn files:

`./nativity.clj path/to/script -c`
