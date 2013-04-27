#!/bin/sh

OUT=$1
URL=$2
SHA1=$3

curl -sfo "$OUT" "$URL" || exit
if [ -n "$SHA1" ]
then
  have=$(openssl sha1 -sha1 -hex <"$OUT" | sed 's/^.* //')
  if ! [ "$SHA1" = "$have" ]
  then
    echo >&2 "fatal: bad content from $URL"
    echo >&2 "       expected $SHA1"
    echo >&2 "       received $have"
  fi
fi
