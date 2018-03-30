#!/bin/sh

set -ex

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

# Have to find where node_modules are installed and set the NODE_PATH

get_node_path() {
    cd $(dirname $node_bin)
    cd ../lib/node_modules
    pwd
}

export NODE_PATH=$(get_node_path)

unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d polygerrit-ui/app
python $TEST_SRCDIR/gerrit/polygerrit-ui/app/template_test_srcs/convert_for_template_tests.py
# Pass a file name argument from the --test_args (example: --test_arg=gr-list-view)
${node_bin} $TEST_SRCDIR/gerrit/polygerrit-ui/app/template_test_srcs/template_test.js $1 $2
