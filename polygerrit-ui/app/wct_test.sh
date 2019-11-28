#!/bin/sh

set -ex

t=$TEST_TMPDIR

ls -l
cp -r -L ./polygerrit-ui/app/* $t/
cp -r -L ./external/ui_npm/node_modules $t/node_modules

ln -s $t/node_modules/@polymer/test-fixture $t/node_modules/test-fixture

export PATH="$(dirname $NPM):$PATH"

cd $t
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ test redirects.json
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ behaviors redirects.json
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ elements redirects.json
test -n "${WCT}"
${WCT} ${WCT_ARGS}
