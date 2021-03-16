#!/bin/bash
usage() {
  f="$(basename -- $0)"
  cat <<EOF
Usage: 
    cd /path/to/All-Projects.git
    "$f [username|gerrit]"

This script finds duplicate usernames only differing in case in the given
account schema ("username" or "gerrit") and their respective accountIds.
EOF
  exit 1
}

if [[ "$#" -ne "1" ]] || ! [[ "$1" =~ ^(gerrit|username)$ ]]; then
  usage
fi

git grep -A1 "\[externalId \"$1:" refs/meta/external-ids \
  | sed -E "/$1/,/accountId/!d" \
  | paste -d ' ' - - \
  | tr \"= : \
  | cut -d: -f 5,8 \
  | tr -d ":" \
  | sort -f \
  | sed -E "s/(.*) (.*)/\2 \1/" \
  | uniq -Di -f1 \
  | sed -E "s/(.*) (.*)/\2 \1/"