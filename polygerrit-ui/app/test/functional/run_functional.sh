#!/usr/bin/env bash

bazel build //polygerrit-ui/app:polygerrit_ui

docker run --rm \
  -p 5900:5900 \
  -v `pwd`/polygerrit-ui/app/test/functional:/tests \
  -v `pwd`/bazel-genfiles/polygerrit-ui/app:/app \
  -it gerrit/polygerrit-functional:v1 \
  /tests/test.js
