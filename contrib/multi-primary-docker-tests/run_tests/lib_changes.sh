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

q() { "$@" > /dev/null 2>&1 ; } # cmd [args...]  # quiet a command

die() { echo -e "$@" ; exit 1 ; } # error_message

ensure() { out=$("$@" 2>&1) || die "ERROR: Failed to $*\n$out" ; } # [args]...

get_change_num() { # < gerrit_push_response > change_num
    local url=$(awk '/^remote: SUCCESS/ { getline; getline; print $2 }')
    echo "${url##*\/}" | tr -d -c '[:digit:]'
}

push_change() { # server project
    local d=$RANDOM file=$RANDOM
    q git clone "ssh://$1:29418/$2" "/tmp/$d"
    q pushd "/tmp/$d"
    q scp -p -P 29418 "$1:hooks/commit-msg" .git/hooks/
    q git reset --hard origin/master
    q touch "$file" && q git add "$file" && q git commit -m "$file"
    ensure git push "ssh://$1:29418/$2" HEAD:refs/for/master
    echo "$out" | get_change_num
}

create_change_through_rest() { # server project
    curl --netrc --request POST \
        --header "Content-Type: application/json" \
        "http://$1:8080/a/changes/" \
        --data '{"project":"'$2'","subject":"test","branch":"master"}' &> /dev/null
}
