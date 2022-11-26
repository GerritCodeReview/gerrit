#!/usr/bin/env bash

# Should be executed from the root of the Gerrit repo:
# polygerrit-ui/app/api/publish.sh
#
# Builds the npm package @gerritcodereview/typescript-api
#
# Adding the `--upload` argument will also publish the package.

set -e

bazel_bin=$(which bazelisk 2>/dev/null)
if [[ -z "$bazel_bin" ]]; then
    echo "Warning: bazelisk is not installed; falling back to bazel."
    bazel_bin=bazel
fi
api_path=polygerrit-ui/app/api

function cleanup() {
  echo "Cleaning up ..."
  rm -f ${api_path}/BUILD
}
trap cleanup EXIT
cp ${api_path}/BUILD_for_publishing_api_only ${api_path}/BUILD

${bazel_bin} build //${api_path}:js_plugin_api_npm_package

if [ "$1" == "--upload" ]; then
  echo 'Uploading npm package @gerritcodereview/typescript-api'
  ${bazel_bin} run //${api_path}:js_plugin_api_npm_package.publish
fi
