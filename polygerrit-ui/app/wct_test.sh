#!/bin/sh

set -ex

t=$TEST_TMPDIR
mkdir -p $TEST_TMPDIR/node_modules
cp -r -L ./external/ui_dev_npm/node_modules/* $t/node_modules

cp -r -L ./polygerrit-ui/app/* $t/
cp -r -L ./external/ui_npm/node_modules/* $t/node_modules/

export PATH="$(dirname $NPM):$PATH"

cd $t
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ ./ test redirects.json
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ ./ behaviors redirects.json
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ ./ elements redirects.json
node $TEST_SRCDIR/$TEST_WORKSPACE/tools/node_tools/links_updater.js ./ ./ scripts redirects.json
test -n "${WCT}"
${WCT} ${WCT_ARGS}
