#!/bin/sh

if test -s $1
then
  echo "$1 not empty:"
  cat "$1"
  exit 1
fi
