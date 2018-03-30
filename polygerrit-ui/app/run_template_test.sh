#!/usr/bin/env bash

# WCT tests are not hermetic, and need extra environment variables.
# TODO(hanwen): does $DISPLAY even work on OSX?
bazel test \
      --test_env="HOME=$HOME" \
      "$@" \
      --test_tag_filters=template \
      //...
