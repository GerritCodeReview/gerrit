#!/usr/bin/env bash

bazel_bin=$(which bazelisk 2>/dev/null)
if [[ -z "$bazel_bin" ]]; then
    echo "Warning: bazelisk is not installed; falling back to bazel."
    bazel_bin=bazel
fi

# WCT tests are not hermetic, and need extra environment variables.
# TODO(hanwen): does $DISPLAY even work on OSX?
${bazel_bin} test \
      --test_env="HOME=$HOME" \
      --test_env="WCT_ARGS=${WCT_ARGS} --expanded --simpleOutput" \
      --test_env="DISPLAY=${DISPLAY}" \
      --test_env="WCT_HEADLESS_MODE=${WCT_HEADLESS_MODE}" \
      --test_output=streamed \
      "$@" \
      //polygerrit-ui/app:wct_test
