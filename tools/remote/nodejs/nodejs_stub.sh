#!/bin/sh
# Self-extracting Node.js: the lines after this stub are a tar archive with
# node and libatomic.so.1. The executor is fresh for every action, so it is
# unpacked into a new directory on each run.
set -eu
# Line on which the tar archive starts, i.e. the line after the last line of
# this file. It is hardcoded so that the bundler can append the archive to the
# stub as is, without templating it. Keep it in sync when editing the stub.
payload_start=14
dir=$(mktemp -d)
tail -n +"$payload_start" "$0" | tar -xf - -C "$dir"
LD_LIBRARY_PATH="$dir${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" exec "$dir/node" "$@"
exit 1
