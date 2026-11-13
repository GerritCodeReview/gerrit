#!/bin/sh
# Runs the given command with the native tools (git, ssh, ssh-keygen) first on
# PATH. The directory is made absolute because tests change their working
# directory.
set -eu
rel=_main/tools/remote/native/tools/bin
for base in "$0.runfiles" "${RUNFILES_DIR:-}" "${TEST_SRCDIR:-}"; do
  if [ -n "$base" ] && [ -d "$base/$rel" ]; then
    bin=$(cd "$base/$rel" && pwd -P)
    PATH="$bin:$PATH"
    export PATH
    exec "$@"
  fi
done
echo "with_native_tools: $rel not found in runfiles" >&2
exit 1
