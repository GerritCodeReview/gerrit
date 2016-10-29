#!/bin/sh

# This script will be run by bazel when the build process starts to
# generate key-value information that represents the status of the
# workspace. The output should be like
#
# KEY1 VALUE1
# KEY2 VALUE2
#
# If the script exits with non-zero code, it's considered as a failure
# and the output will be discarded.

for p in . $(ls plugins); do
  if test "$p" = .; then
    dir=.
    nameUpper=GERRIT
  else
    nameUpper=$(tr '[:lower:]' '[:upper:]' <<<"$p")
    dir="plugins/$p"
  fi
  test -d "$dir" || continue
  label=$(git -C "$dir" describe --always --match "v[0-9].*" --dirty)
  if test -n "$label"; then
    echo "STABLE_BUILD_${nameUpper}_LABEL $label"
  else
    echo "git describe failed for directory \`$dir'" >&2
  fi
done
