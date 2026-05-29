#!/usr/bin/env bash

bazel_bin=$(which bazelisk 2>/dev/null)
if [[ -z "$bazel_bin" ]]; then
    echo "Warning: bazelisk is not installed; falling back to bazel."
    bazel_bin=bazel
fi

# At least temporarily we want to know what is going on even when all tests are
# passing, so we have a better chance of debugging what happens in CI test runs
# that were supposed to catch test failures, but did not.
# Run type checker before testing
${bazel_bin} build //polygerrit-ui/app:compile_pg_with_tests && \
${bazel_bin} test \
      "$@" \
      --test_verbose_timeout_warnings \
      --test_output=all \
      //polygerrit-ui:web_test_runner
