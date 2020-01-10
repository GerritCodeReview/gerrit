#!/usr/bin/env bash

SKIP="skip"

if [[ $1 == $SKIP ]]; then
    echo "Running...."
else
    # for running from npm
    bazel build //polygerrit-ui/app:closure_test_bin
fi