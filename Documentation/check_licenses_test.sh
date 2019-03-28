#!/bin/sh

hook=$(pwd)/resources/com/google/gerrit/server/tools/root/hooks/commit-msg

for f in  js_licenses licenses ; do
  if ! diff -u Documentation/${f}.txt Documentation/${f}.gen.txt  ; then
     echo ""
     echo "FAIL: ${f}.txt out of date"
     echo "to fix: "
     echo ""
     echo "  cp bazel-genfiles/Documentation/${f}.gen.txt Documentation/${f}.txt"
     echo ""
     exit 1
  fi
done
