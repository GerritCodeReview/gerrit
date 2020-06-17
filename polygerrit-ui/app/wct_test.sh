#!/bin/sh

set -ex
root_dir=$(pwd)
t=$TEST_TMPDIR
export JSON_CONFIG=$2

mkdir -p $t/node_modules
# WCT doesn't implement node module resolution.
# WCT uses only node_module/ directory from current directory when looking for a module
# So, it is impossible to make hierarchical node_modules. Instead, we copy
# all node_modules to one directory.
cp -R -L ./external/ui_dev_npm/node_modules/* $t/node_modules

# Copy ui_npm, so it will override ui_dev_npm modules (in case of conflicts)
# Because browser always requests specific exact files (i.e. not a directory),
# it always receives file from ui_npm. It can broke WCT itself but luckily it works.
cp -R -L ./external/ui_npm/node_modules/* $t/node_modules

cp -R -L ./polygerrit-ui/app/* $t/

export PATH="$(dirname $NPM):$PATH"

cd $t
# The ts_test_out contains js files produced by typescript compiler
# Copy it to the parent folder to get correct directory layout
# The total size of files is quite small, so using cp instead of mv shouldn't
# be a problem.
# Using mv directly is not possible here (it can't merge directories).
# Other solutions are possible, but require additional efforts to make them
# work on Mac and Linux. Since we are going to convert WCT tests to Karma tests
# soon, we will use the simplest solution here.
cp -R ./ts_test_out/* ./
rm -rf ./ts_test_out
# Without the follwoing removing, recreating the suite_conf.js fails with the
# "Permission denied" error
rm ./test/suite_conf.js

echo "export const config=$JSON_CONFIG;" > ./test/suite_conf.js
echo "export const testsPerFileString=\`" >> ./test/suite_conf.js
# Count number of tests in each file.
# We don't need accurate data, use simplest method
# TODO(dmfilippov): collect data only once
# In the current implementation, the same data is collected for each split,
# It takes less than a second which many times less than the overall wct test time
grep -rnw '.' --include=\*_test.html -e "test(" -c >> ./test/suite_conf.js
echo "\`;" >>./test/suite_conf.js

# If wct doesn't receive any parameters, it fails (can't find files)
# Pass --config-file as a parameter to have some arguments in command line
$root_dir/$1 --config-file wct.conf.js ${WCT_ARGS}
