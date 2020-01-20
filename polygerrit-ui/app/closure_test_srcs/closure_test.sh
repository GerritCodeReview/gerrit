#!/bin/bash
set -ex

node_bin=$(which node) && true
if [ -z "$node_bin" ]; then
    echo "node must be on the path."
    exit 1
fi

npm_bin=$(which npm) && true
if [[ -z "$npm_bin" ]]; then
    echo "NPM must be on the path. (https://www.npmjs.com/)"
    exit 1
fi

# Have to find where node_modules are installed and set the NODE_PATH

get_node_path() {
    cd $(dirname $node_bin)
    cd ../lib/node_modules
    pwd
}

export NODE_PATH=$(get_node_path)

# Pass a file name argument from the --test_args (example: --test_arg=gr-list-view)
${node_bin} polygerrit-ui/app/closure_test_srcs/closure_test.js $@
