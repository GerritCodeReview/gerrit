#!/bin/sh

set -ex
root_dir=$(pwd)
t=$TEST_TMPDIR

mkdir -p $t/node_modules
# WCT doesn't implement node module resolution.
# WCT uses only node_module/ directory from current directory when looking for a module
# So, it is impossible to make hierarchical node_modules. Instead, we copy
# all node_modules to one directory.
cp -R -L ./external/ui_dev_npm/node_modules/* $t/node_modules

# Copy ui_npm, so it will override ui_dev_npm modules (in case of conflicts)
# Because browser always requests specific exact files (i.e. not a directory),
# it always receives file from ui_npm. It can broke WCT itself but luckely it works.
cp -R -L ./external/ui_npm/node_modules/* $t/node_modules

cp -R -L ./polygerrit-ui/app/* $t/

export PATH="$(dirname $NPM):$PATH"

cd $t
ls -l ./node_modules/web-component-tester/runner/browserrunner.js
sed -i'' -e 's|onEvent(event, data) {|onEvent(event, data) {console.log("OnEvent!!!!", event, (new Date()).toString());console.log(data);|g' ./node_modules/web-component-tester/runner/browserrunner.js
cat ./node_modules/web-component-tester/runner/browserrunner.js

# If wct doesn't receive any paramenters, it fails (can't find files)
# Pass --config-file as a parameter to have some arguments in command line
$root_dir/$1 --config-file wct.conf.js ${WCT_ARGS}
