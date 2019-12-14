#!/bin/bash

# TODO(dmfilippov): Update template_test to support Polymer 2/Polymer 3 or delete it completely
# The following line temporary disable template tests. Existing implementation doesn't compatible
# with Polymer 2 & 3 class-based components. Polymer linter makes some checks regarding
# templates and binding, but not all.
exit 0

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

unzip -o polygerrit-ui/polygerrit_components.bower_components.zip -d polygerrit-ui/app
python $TEST_SRCDIR/gerrit/polygerrit-ui/app/template_test_srcs/convert_for_template_tests.py
# Pass a file name argument from the --test_args (example: --test_arg=gr-list-view)
${node_bin} $TEST_SRCDIR/gerrit/polygerrit-ui/app/template_test_srcs/template_test.js $1 $2
