#!/usr/bin/env bash

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path. (https://www.npmjs.com/)"
    exit 1
fi

wct_bin=$(which wct)
if [[ -z "$wct_bin" ]]; then
    echo "WCT must be on the path. (https://github.com/Polymer/web-component-tester)"
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
      "$@" \
      //polygerrit-ui/app:wct_test
