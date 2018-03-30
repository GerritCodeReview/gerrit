#!/bin/sh

set -ex

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
