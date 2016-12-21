#!/bin/sh

filtered="$1.filtered"

cat $1 \
  | grep -v "@bcpg//jar:jar" \
  | grep -v "@bcpkix//jar:jar" \
  | grep -v "@bcprov//jar:jar" \
  > $filtered

if test -s $filtered
then
  echo "$filtered not empty:"
  cat $filtered
  exit 1
fi
