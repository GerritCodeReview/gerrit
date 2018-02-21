#!/usr/bin/env bash

bazel build //polygerrit-ui/app:polygerrit_ui

pushd ./polygerrit-ui/app/test/functional > /dev/null
trap popd EXIT

docker-compose up --abort-on-container-exit --force-recreate
