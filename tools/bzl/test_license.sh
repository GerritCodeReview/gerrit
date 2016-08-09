#!/bin/sh

filtered="$1.filtered"

cat $1 \
  | grep -v "//lib/bouncycastle:bcpg" \
  | grep -v "//lib/bouncycastle:bcpkix" \
  | grep -v "//lib/bouncycastle:bcprov" \
  > $filtered

if test -s $filtered
then
  echo "$filtered not empty:"
  cat $filtered
  exit 1
fi
