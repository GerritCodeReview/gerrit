#!/bin/bash

set -ex

DIR=$(pwd)
ln -s $RUNFILES_DIR/ui_npm/node_modules $TEST_TMPDIR/node_modules
cp $2 $TEST_TMPDIR/polymer.json
cp -R -L polygerrit-ui/app/__ts__out__polygerrit_ui/* $TEST_TMPDIR
# Polylinter uses gr-app.html as an entry point.
cp -R -L polygerrit-ui/app/elements/gr-app.html $TEST_TMPDIR/elements/

#Can't use --root with polymer.json - see https://github.com/Polymer/tools/issues/2616
#Change current directory to the root folder
cd $TEST_TMPDIR/
$DIR/$1 lint --verbose
