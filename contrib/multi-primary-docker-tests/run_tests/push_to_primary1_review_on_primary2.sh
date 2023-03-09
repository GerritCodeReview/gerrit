#!/usr/bin/env bash
#
# Copyright (C) 2023 The Android Open Source Project
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

readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
source "$MYDIR"/lib_result.sh

die() { echo -e "$@" ; exit 1 ; } # error_message

ensure() { out=$("$@" 2>&1) || die "ERROR: Failed to $*\n$out" ; } # [args]...

get_change_num() { # < gerrit_push_response > changenum
    local url=$(awk '/^remote: SUCCESS/ { getline; getline; print $2 }')
    echo "${url##*\/}" | tr -d -c '[:digit:]'
}

PRIMARY_1=$1
PRIMARY_2=$2
PROJECT=$3
[ -n "$PRIMARY_1" ] || die "PRIMARY_1 not set"
[ -n "$PRIMARY_2" ] || die "PRIMARY_2 not set"
[ -n "$PROJECT" ] || die "PROJECT not set"

d=$RANDOM
git clone ssh://$PRIMARY_1:29418/$PROJECT "/tmp/$d" &> /dev/null
pushd "/tmp/$d" &> /dev/null
scp -p -P 29418 $PRIMARY_1:hooks/commit-msg .git/hooks/ &> /dev/null

git reset --hard origin/master > /dev/null
file=$RANDOM
touch "$file" && git add "$file" && git commit -m "$file" &> /dev/null

ensure git push "ssh://$PRIMARY_1:29418/$PROJECT" HEAD:refs/for/master
change_num=$(echo "$out" | get_change_num)
out=$(ssh -p 29418 "$PRIMARY_2" gerrit review --code-review +1 "${change_num},1" 2>&1)
result "Push to primary1 and review on primary2" "$out"

exit $RESULT
