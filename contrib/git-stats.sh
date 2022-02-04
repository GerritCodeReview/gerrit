#!/usr/bin/env bash
#
# Copyright (C) 2022 The Android Open Source Project
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

usage() { # error_message
    local prog=$(basename -- "$0")
    cat <<EOF
Run in one of the three forms below:
$ $prog /path/to/git/dir
$ find . -type d -name '*.git' -prune -print0 | xargs -0 -n 1 $prog > <file>
$ $prog -s <file>

-h                   usage/help
-s <stats file>      Print a summary of previously collected stats from the
                     given file.

Collect statistics on one or more git repositories.

Use the first form to see the statistics for a single repo, the second form to
collect statistics on all bare repos under path into a file, and the third form
for summarizing the statistics collected into a file in the second form.

The given repositories can be bare or non-bare, but the provided path must be
the '.git' dir.

EOF

    [ -n "$1" ] && info "ERROR $1"

    exit 128
}

info() { echo "$1" >&2 ; }

stats() {
    pushd "$1" > /dev/null 2>&1

    echo "loose_refs: $(find refs/ -type f | wc -l)/"
    echo "loose_ref_dirs: $(find refs/ -type d | wc -l)"
    echo "all_refs: $(git show-ref | wc -l)"

    git count-objects -v
    popd > /dev/null 2>&1
}

summarize_field() { # name
  echo "Largest $1"
  awk -F'|' '$2~"'"$1"'" {print $2, $1}' | awk '{print $2, $3}' | sort -nr | \
      head
}

summarize_largest() {
    local out=$(cat -)
    echo "$out" | summarize_field loose_refs
    echo "$out" | summarize_field loose_ref_dirs
    echo "$out" | summarize_field all_refs
    echo "$out" | summarize_field packs
    echo "$out" | summarize_field count
    echo "$out" | summarize_field in-pack
    echo "$out" | summarize_field refs
    echo "$out" | summarize_field size
}

if [ "$1" == "-h" ] ; then
    usage
elif [ "$1" == "-s" ] ; then
    summarize_largest < "$2"
else
    stats "$1" | awk '{print "'"$1"'|" $0}'
fi
