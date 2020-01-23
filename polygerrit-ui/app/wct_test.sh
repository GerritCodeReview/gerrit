#!/bin/sh

set -ex
root_dir=$(pwd)
t=$TEST_TMPDIR

mkdir -p $t/node_modules
# WCT doesn't implement node module resolution.
# WCT uses only node_module/ directory from current directory when looking for a module
# So, it is impossible to make hierarichal node_modules. Instead, we copy
# all node_modules to one directory.
cp -r -L ./external/ui_dev_npm/node_modules/* $t/node_modules

# Copy ui_npm, so it will override ui_dev_npm modules (in case of conflicts)
# Beacuse browser always requests specific exact files (i.e. not a directory),
# it always receives file from ui_npm. It can broke WCT itself but luckely it works.
cp -r -L ./external/ui_npm/node_modules/* $t/node_modules

cp -r -L ./polygerrit-ui/app/test-srcs-updated-links/polygerrit-ui/app/* $t/

export PATH="$(dirname $NPM):$PATH"

cd $t
test -n "${WCT}"

ls -l
${WCT} ${WCT_ARGS}
