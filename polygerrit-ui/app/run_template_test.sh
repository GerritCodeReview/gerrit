#!/usr/bin/env bash


if [[ -z "${TEMPLATE_NO_DEFAULT}" ]]; then
bazel test \
      --test_env="HOME=$HOME" \
      //polygerrit-ui/app:all
      --test_tag_filters=template \
      "$@" \
      --test_output errors \
      --nocache_test_results
else
bazel test \
      --test_env="HOME=$HOME" \
      "$@" \
      --test_output errors \
      --nocache_test_results
fi
