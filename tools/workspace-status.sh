#!/bin/sh

# This script will be run by bazel when the build process starts to
# generate key-value information that represents the status of the
# workspace. The output should be like
#
# KEY1 VALUE1
# KEY2 VALUE2
#
# If the script exits with non-zero code, it's considered as a failure
# and the output will be discarded.

# The code below presents an implementation that works for git repository
git_rev=$(git describe HEAD)

# Check whether there are any uncommited changes
tree_status=""
git diff-index --quiet HEAD --
if [[ $? != 0 ]]; then
    tree_status="-dirty"
fi

echo "BUILD_GERRIT_LABEL ${git_rev}${tree_status}"
