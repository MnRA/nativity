# nativity

A small script for turning babashka scripts into native binaries

## Requirements

   - JVM (I think?)
   - babashka
   - GraalVM

## Usage

  From the nativity directory: `bb nativity.clj path/to/script`
  or even just `./nativity.clj path/to/script`

### Options

  #### Modes
  you can specify which mode to run on by using the `./nativity.clj path/to/script -m implicit` it will default to "untouched"

  ##### Implicit

  This mode is for when you made a script using the implicit requires that babashka has for one-liners

  ##### Untouched

  Untouched will leave the file completely alone, make a copy into the src directory and create the deps.edn file


  #### Import require specific things for implicit mode
  You can specify which of the built in bb dependencies to require by using the `./nativity.clj path/to/script -d io,str,json`.
  If you use `-d all` it will include all of them.

  #### Rename binary
  if you want your binary to have a different name:
  `./nativity.clj path/to/script -n name-of-binary`

  #### Clean files
  With this flag you can make it automatically remove the generated src and deps.edn files:

  `./nativity.clj path/to/script -n name-of-binary`
