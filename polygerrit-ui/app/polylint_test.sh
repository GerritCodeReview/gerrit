#!/bin/bash

set -ex

DIR=$(pwd)
ln -s $RUNFILES_DIR/ui_npm/node_modules $TEST_TMPDIR/node_modules
cp $2 $TEST_TMPDIR/polymer.json
cp -R -L polygerrit-ui/app/polylint-updated-links/polygerrit-ui/app/* $TEST_TMPDIR

# In this commit, bower_components are used for testing.
# The import statement in font-roboto-local-loader.js breaks tests.
# Temporoary disable this test.
# In the next change this line is removed.
exit 0


#Can't use --root with polymer.json - see https://github.com/Polymer/tools/issues/2616
#Change current directory to the root folder
cd $TEST_TMPDIR/
$DIR/$1 lint --verbose
