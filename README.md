# nativity

A small script for turning babashka scripts into native binaries

## Requirements

   - JVM (because it depends on [clj.native-image](https://github.com/taylorwood/clj.native-image))
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

(when-not (System/getProperty "babashka.main")
  (when (find-ns 'babashka.classpath) (apply -main *command-line-args*)))
```
This can compile as is because it has:
  * Ns form with :gen-class and explicit requires (`(ns which
    (:require [clojure.java.io :as io])
    (:gen-class))`)
  * an entry point (`defn -main [& args]`)
  * no in-line require forms (`(require ....)`)

##### Directed
This mode cuts out parts of your script and puts it into a main function for you:
for example assume you have this script:
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

 (when-first [executable *command-line-args*]
   (println (where executable)))
```
and you run `./nativity.clj which.clj -m directed -w 17-18` it will wrap lines 17 to 18 in the main entry point (in this case those lines would be the last 2 at the end), making a compilable script.

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
``` shellsession
Short     Long                    Default               Description
 -d,   --deps LIST                  []         Specify the dependency list for implicit mode (comma separated values, example: "io,str,json,edn")
 -n,   --name FILENAME                         Override default file name for the binary (will default to the input file name )
 -c,   --clean                                 Clean up the src and deps.edn files
 -m,   --mode MODE               untouched     Choose the processing mode. Currently available: implicit, untouched
       --no-compile                            Don't run binary compilation step
       --namespace NAMESPACE-NAME              If your main function is in a namespace different from your file name you can override with this. It will also use this to name the namespace in implicit mode
 -w,   --wrap RANGE                [0 0]       Determine the lines you want to want to wrap with main (Only directed mode)
```
#### Require specific things for implicit mode
You can specify which of the built in bb dependencies to require by using the `./nativity.clj path/to/script -d io,str,json`.
If you use `-d all` it will include all of them.

#### Rename binary
if you want your binary to have a different name:
`./nativity.clj path/to/script -n name-of-binary`

#### Clean files
With this flag you can make it automatically remove the generated src and deps.edn files:

`./nativity.clj path/to/script -c`
