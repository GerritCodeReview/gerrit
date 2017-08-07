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
    echo "For more information, view the README:"
    echo "https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/#Style-guide"
    exit 1
fi

# Have to find where node_modules are installed and set the NODE_PATH
dir=$(pwd)
cd $(dirname $node_bin)
cd ../lib/node_modules
export NODE_PATH=$(pwd)
cd $dir

unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d polygerrit-ui/app
python $TEST_SRCDIR/gerrit/polygerrit-ui/app/template_test_srcs/convert_to_goog_module.py
${node_bin} $TEST_SRCDIR/gerrit/polygerrit-ui/app/template_test_srcs/template_test.js
