#!/bin/bash

set -euo pipefail
# The following is nessecary, because bazel uses links for node_modules
# Without the NODE_PRESERVE_SYMLINKS options, the karma server tries to
# load plugin in browser from a "random" place. As a result, it generate
# html for browser with the paths like "/absolute/users/username/gerrit/...",
# but it must be "/base/external/ui_dev_npm
# This happens, because node's method require.resolve by default returns
# a resolved path.
# It can work locally without the NODE_PRESERVE_SYMLINKS, but doesn't work
# on gerrit-CI
export NODE_PRESERVE_SYMLINKS=1
./$1 start $2 --single-run
