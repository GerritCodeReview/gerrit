# Building Highlight.js for Gerrit

Highlight JS needs to be built with specific language support. Here are the
steps to build the minified file that appears here.

NOTE: If you are adding support for a language to Highlight.js make sure to add
it to the list of languages in the build command below.

## Prerequisites

You will need:

* nodejs
* closure-compiler
* git

## Steps to Create the Pack File

The packed version of Highlight.js is an un-minified JS file with all of the
languages included. Build it with the following:

    $>  # start in some temp directory
    $>  git clone https://github.com/isagalaev/highlight.js.git
    $>  cd highlight.js
    $>  node tools/build.js -n \
          bash \
          cpp \
          cs \
          clojure \
          css \
          d \
          dart \
          dockerfile \
          erb \
          go \
          haskell \
          ini \
          java \
          javascript \
          json \
          kotlin \
          less \
          lisp \
          lua \
          makefile \
          markdown \
          nginx \
          objectivec \
          ocaml \
          perl \
          php \
          prolog \
          protobuf \
          puppet \
          python \
          ruby \
          rust \
          scala \
          shell \
          sql \
          swift \
          typescript \
          xml \
          yaml

The resulting JS file will appear in the "build" directory of the Highlight.js
repo under the name "highlight.pack.js".

## Minification

Minify the file using closure-compiler using the command below. (Modify
`/path/to` with the path to your compiler jar.)

    $>  java -jar /path/to/closure-compiler.jar \
            --js build/highlight.pack.js \
            --js_output_file build/highlight.min.js

Copy the header comment that appears on the first line of
build/highlight.pack.js and add it to the start of build/highlight.min.js.

## Finish

Copy the resulting build/highlight.min.js file to lib/highlightjs
