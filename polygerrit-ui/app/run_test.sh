#!/usr/bin/env bash

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path. (https://www.npmjs.com/)"
    exit 1
fi

# From https://www.linuxquestions.org/questions/programming-9/bash-script-return-full-path-and-filename-680368/page3.html
function abs_path {
  if [[ -d "$1" ]]
  then
      pushd "$1" >/dev/null
      pwd
      popd >/dev/null
  elif [[ -e $1 ]]
  then
      pushd "$(dirname "$1")" >/dev/null
      echo "$(pwd)/$(basename "$1")"
      popd >/dev/null
  else
      echo "$1" does not exist! >&2
      return 127
  fi
}
wct_bin=$(which wct)
if [[ -z "$wct_bin" ]]; then
  wct_bin=$(abs_path ./node_modules/web-component-tester/bin/wct);
fi
if [[ -z "$wct_bin" ]]; then
    echo "wct_bin must be set or WCT locally installed (npm install wct)."
    exit 1
fi

# WCT tests are not hermetic, and need extra environment variables.
# TODO(hanwen): does $DISPLAY even work on OSX?
bazel test \
      --test_env="HOME=$HOME" \
      --test_env="WCT=${wct_bin}" \
      --test_env="WCT_ARGS=${WCT_ARGS}" \
      --test_env="NPM=${npm_bin}" \
      --test_env="DISPLAY=${DISPLAY}" \
      --test_env="WCT_HEADLESS_MODE=${WCT_HEADLESS_MODE}" \
      "$@" \
      //polygerrit-ui/app:embed_test \
      //polygerrit-ui/app:wct_test
