#!/usr/bin/env bash

bazel_bin=$(which bazelisk 2>/dev/null)
if [[ -z "$bazel_bin" ]]; then
    echo "Warning: bazelisk is not installed; falling back to bazel."
    bazel_bin=bazel
fi

${bazel_bin} test \
      "$@" \
      //polygerrit-ui:karma_test
