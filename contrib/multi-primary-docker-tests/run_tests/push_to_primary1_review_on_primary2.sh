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
source "$MYDIR"/lib_changes.sh

PRIMARY_1=$1
PRIMARY_2=$2
PROJECT=$3
[ -n "$PRIMARY_1" ] || die "PRIMARY_1 not set"
[ -n "$PRIMARY_2" ] || die "PRIMARY_2 not set"
[ -n "$PROJECT" ] || die "PROJECT not set"

for i in `seq 1 10`; do
    change_num=$(push_change "$PRIMARY_1" "$PROJECT")
    out=$(ssh -p 29418 "$PRIMARY_2" gerrit review --code-review +1 "${change_num},1" 2>&1)
    result "Push to primary1 and review on primary2 #$i" "$out"
done

exit $RESULT
