#!/bin/bash

set -ex

DIR=$(pwd)
ln -s $RUNFILES_DIR/ui_npm/node_modules $TEST_TMPDIR/node_modules
cp $2 $TEST_TMPDIR/polymer.json
cp -R -L polygerrit-ui/app/_pg_ts_out/* $TEST_TMPDIR

#Can't use --root with polymer.json - see https://github.com/Polymer/tools/issues/2616
#Change current directory to the root folder
cd $TEST_TMPDIR/
cat polymer.json
ls -l
ls -l elements/
ls -l elements/shared/
ls -l elements/shared/gr-js-api-interface/
ls -l elements/shared/gr-js-api-interface/gr-plugin-rest-api.js
$DIR/$1 lint --verbose
