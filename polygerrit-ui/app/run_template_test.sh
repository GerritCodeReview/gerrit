#!/usr/bin/env bash

node_bin=$(which node)
if [ -z "$node_bin" ]; then
    echo "node must be on the path."
    exit 1
fi

npm_bin=$(which npm)
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path. (https://www.npmjs.com/)"
    exit 1
fi

fried_twinkie_config=$(npm list -g | grep -c fried-twinkie)
typescript_config=$(npm list -g | grep -c typescript)
if [ -z "$npm_bin" ] || [ "$fried_twinkie_config" -eq "0" ]; then
    echo "You must install fried twinkie and its dependencies from NPM."
    echo "> npm install -g fried-twinkie"
    exit 1
fi

twinkie_version=$(npm list -g fried-twinkie@\>0.1 | grep fried-twinkie || :)
if [ -z "$twinkie_version" ]; then
    echo "Outdated version of fried-twinkie found. Bypassing template check."
    exit 0
fi

# WCT tests are not hermetic, and need extra environment variables.
# TODO(hanwen): does $DISPLAY even work on OSX?
bazel test \
      --test_env="HOME=$HOME" \
      --test_env="NPM=${npm_bin}" \
      "$@" \
      --test_tag_filters=template \
      //...
