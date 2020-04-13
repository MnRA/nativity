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
  You can specify which of the built in bb dependencies to require by using the `/nativity.clj path/to/script -d io,str,json`.
  If you use `-d all` it will include all of them.

  You can rename the output binary by using the -n flag `/nativity.clj path/to/script -n name-of-binary`
