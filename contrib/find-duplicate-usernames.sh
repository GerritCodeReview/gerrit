#!/bin/bash
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
usage() {
  f="$(basename -- $0)"
  cat <<EOF
Usage:
    cd /path/to/All-Users.git
    "$f [username|gerrit|external]"

This script finds duplicate usernames only differing in case in the given
account schema ("username", "gerrit" or "external") and their respective accountIds.
EOF
  exit 1
}

if [[ "$#" -ne "1" ]] || ! [[ "$1" =~ ^(gerrit|username|external)$ ]]; then
  usage
fi

# 1. find lines with user name and subsequent line in external-ids notes branch
#    example output of git grep -A1 "\[externalId \"username:" refs/meta/external-ids:
#    refs/meta/external-ids:00/1d/abd037e437f71d42134e6ad532a06948a2ba:[externalId "username:johndoe"]
#    refs/meta/external-ids:00/1d/abd037e437f71d42134e6ad532a06948a2ba-      accountId = 1000815
#    --
#    refs/meta/external-ids:00/1f/0270fc2a6fc3a2439c454c8ab0c75323fdb0:[externalId "username:JohnDoe"]
#    refs/meta/external-ids:00/1f/0270fc2a6fc3a2439c454c8ab0c75323fdb0-      accountId = 1000816
#    --
# 2. remove group separators
# 3. remove line break between user name and accountId lines
# 4. unify separators to ":"
# 5. cut on ":", select username and accountId fields
# 6. sort case-insensitive
# 7. flip columns
# 8. uniq case-insensitive, only show duplicates, avoid comparing first field
# 9. flip columns back
git grep -A1 "\[externalId \"$1:" refs/meta/external-ids -- \
  | sed -E "/$1/,/accountId/!d" \
  | paste -d ' ' - - \
  | tr \"= : \
  | cut -d: --output-delimiter="" -f 5,8 \
  | sort -f \
  | sed -E "s/(.*) (.*)/\2 \1/" \
  | uniq -Di -f1 \
  | sed -E "s/(.*) (.*)/\2 \1/"
