#!/bin/bash

set -ex

DIR=$(pwd)
ln -s $RUNFILES_DIR/ui_npm/node_modules $TEST_TMPDIR/node_modules
cp $2 $TEST_TMPDIR/polymer.json
cp -R -L polygerrit-ui/app/polylint-updated-links/polygerrit-ui/app/* $TEST_TMPDIR

#Can't use --root with polymer.json - see https://github.com/Polymer/tools/issues/2616
#Change current directory to the root folder
cd $TEST_TMPDIR/
$DIR/$1 lint --verbose
