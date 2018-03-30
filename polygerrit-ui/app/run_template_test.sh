#!/usr/bin/env bash

bazel test \
      --test_env="HOME=$HOME" \
      --test_tag_filters=template \
      --test_output errors \
      --nocache_test_results \
      "$@" \
      //...
