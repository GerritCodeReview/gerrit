#!/bin/sh

set -ex

npm_bin=$(which npm)
if [ -z "$npm_bin" ]; then
    echo "NPM must be on the path."
    exit 1
fi

node_bin=$(which node)
if [ -z "$node_bin" ]; then
    echo "node must be on the path."
    exit 1
fi

fried_twinkie_config=$(npm list -g | grep -c fried-twinkie)
typescript_config=$(npm list -g | grep -c typescript)
if [ -z "$npm_bin" ] || [ "$fried_twinkie_config" -eq "0" ]; then
    echo "You must install fried twinkie and its dependencies from NPM."
    echo "> npm install -g fried-twinkie"
    exit 1
fi

# Have to find where node_modules are installed and set the NODE_PATH

get_node_path() {
    cd $(dirname $node_bin)
    cd ../lib/node_modules
    pwd
}

export NODE_PATH=$(get_node_path)


unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d "$TEST_SRCDIR/gerrit/polygerrit-ui/app"

cd "$TEST_SRCDIR/gerrit/polygerrit-ui"

echo $(ls -l)

python app/template_test_srcs/convert_for_template_tests.py
${node_bin} app/template_test_srcs/template_test.js
