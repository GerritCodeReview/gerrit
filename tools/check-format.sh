#!/bin/sh

# Usage:
#
# 1) to check the current working tree
#   tools/bzl/check-format.sh
#
# 2) to check a specific commit for conformance:
#   tools/bzl/check-format.sh commit

tmp=$(mktemp -d /tmp/gjf.XXXXXX)

bazel build tools/bzl:google-java-format >& ${tmp}/blaze.log
if [[ "$?" != "0" ]]; then
  cat ${tmp}/blaze.log
  echo ""
  echo "bazel failed"
  echo ""
  exit 1
fi

src=${1:-}

if [[ -n "$1" ]]; then
else
  src=""
fi

set -eu

mkdir ${tmp}/{before,after}
GJF=${PWD}/bazel-bin/tools/bzl/google-java-format

if [[ -n "${src}" ]]; then
  for f in $(git diff --no-renames --diff-filter=AM --name-only "${src}^" "${src}" | grep '\.java$' ); do
    dest=${tmp}/before/${f}
    mkdir -p $(dirname $dest)
    git show "${src}":"${f}" > ${dest}
  done
else
  for f in $(git diff --name-only HEAD | grep '\.java$' ); do
    dest=${tmp}/before/${f}
    mkdir -p $(dirname $dest)
    cp ${f} ${dest}
  done
fi

if [[ -z "$(find ${tmp}/before -type f)" ]]; then
  echo "SUCCESS: No Java files found"
  exit 0
fi

cp -a ${tmp}/before/* ${tmp}/after/

(cd ${tmp}/after && ${GJF} -i $(find -type f))

set +e
cd ${tmp} && diff -ur before after > ${tmp}/diffs

if [[ -s "${tmp}/diffs" ]]; then
  egrep -v '^(\+\+\+|diff)' ${tmp}/diffs
  echo ""
  echo "FAIL: found formatting problems."

  exit 1
fi

echo SUCCESS
