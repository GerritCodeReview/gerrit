#!/bin/bash
#
# SPDX-FileCopyrightText: Copyright (c) 2024 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
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

ext() { echo "${1##*\.}" ; } # <filename>.<ext> > <ext>

steps() { awk '/=== Step:/{printf("%s ", $3)}' | sed -es'/ $//' ; }

MYPROG=$(readlink -f -- "$BASH_SOURCE")
MYPATH=$(dirname -- "$MYPROG")
TEST_PROG_DIR=$(dirname -- "$MYPATH")
TEST_PROG=$TEST_PROG_DIR/gcng.sh
. "$MYPATH"/lib_result.sh
. "$MYPATH"/lib.sh

### --- Setup test repo ----

REPOS=$MYPATH/test_repos
mkdir -p -- "$REPOS"

REPO=$REPOS/test_gcng_repo
GD=$REPO/.git
GD_PACK=$GD/objects/pack
GD_PRES=$GD_PACK/preserved

q rm -rf "$REPO"
q git init "$REPO"
create_commit
mkdir -p -- "$GD_PRES"

cd "$GD"


OUT=$("$TEST_PROG" -n invalid 2>&1)
[ $? -eq 1 ]
result "invalid argument" "$OUT"

! OUT=$("$TEST_PROG" --git-dir=non-existant repack 2> /dev/null)
result "failed repack exits with error code"

# -------------------- Steps --------------------

DEF_GIT_OPTS="-c pack.island=refs/heads/ -c pack.island=refs/tags/"
OUT=$("$TEST_PROG" -n gc)
result_success "simple gc"
OUT=$(echo "$OUT" | grep "cmd: git .* gc")
result_out "$RESULT_NAME" "  cmd: git $DEF_GIT_OPTS gc" "$OUT"

OUT=$("$TEST_PROG" -n repack)
result_success "simple repack"
OUT=$(echo "$OUT" | grep "cmd: git .* repack")
result_out "$RESULT_NAME" "  cmd: git $DEF_GIT_OPTS repack" "$OUT"

OUT=$("$TEST_PROG" -n --git-dir "$GD" git-repack)
result_success "git-repack step only"
STEPS=$(echo "$OUT" | steps)
result_out "$RESULT_NAME" "git-repack" "$STEPS"

OUT=$("$TEST_PROG" -n --git-dir "$GD" preserve,git-repack -A -d)
result_success "preserve and git-repack steps"
STEPS=$(echo "$OUT" | steps)
result_out "$RESULT_NAME" "preserve git-repack" "$STEPS"
OUT=$(echo "$OUT" | grep "cmd: git .* repack")
result_out "git-repack has args when substep" "  cmd: git --git-dir $GD $DEF_GIT_OPTS repack -A -d" "$OUT"

OUT=$("$TEST_PROG" -n --git-dir "$GD" -c gc.preserveoldpacks=false repack -A -d)
result_success "disable preserve step"
STEPS=$(echo "$OUT" | steps)
echo "$STEPS" | grep -q -v "preserve git-repack"
result "$RESULT_NAME" "$STEPS"

# -------------------- repack-lock --------------------

touch "$GD"/gc.pid
OUT=$("$TEST_PROG" -n repack 2> /dev/null)
result_error "repack gc.pid locked"
result_out "$RESULT_NAME" "" "$(echo "$OUT" | grep 'git .* repack')"
OUT=$("$TEST_PROG" -n -c repack.locker=true repack 2> /dev/null)
result_success "repack.locker unlocked"
result_out "$RESULT_NAME" "  cmd: git -c repack.locker=true $DEF_GIT_OPTS repack" "$(echo "$OUT" | grep 'git .*repack')"
rm "$GD"/gc.pid

OUT=$("$TEST_PROG" -n -c repack.locker=false repack 2> /dev/null)
result_error "repack.locker locked"
result_out "$RESULT_NAME" "" "$(echo "$OUT" | grep 'git .*repack')"

# -------------------- git-dir --------------------

rgrep() {
    echo "$OUT" | grep 'cmd: git .* repack' | sed -es'/^  cmd: git //'
}

fgrep() {
    echo "$OUT" | grep -e 'find .*refs' | sed -es'/^  cmd: // ; s/\(refs\).*/\1/'
}

EXPF="find $GD/refs"
OUT=$("$TEST_PROG" -n --git-dir "$GD" repack)
result_success "--git-dir"
result_out "--git-dir for command" "--git-dir $GD $DEF_GIT_OPTS repack" "$(rgrep)"
result_out "--git-dir for find" "$EXPF" "$(fgrep)"
result_out "indicates repo" "Running Next Gen Git GC on repo: $GD" "$(echo "$OUT" | grep 'Running Next Gen Git GC')"

OUT=$("$TEST_PROG" -n --git-dir="$GD" repack)
result_success "--git-dir="
result_out "--git-dir= for command" "--git-dir=$GD $DEF_GIT_OPTS repack" "$(rgrep)"
result_out "--git-dir= for find" "$EXPF" "$(fgrep)"

OUT=$(GIT_DIR=$GD "$TEST_PROG" -n repack)
result_success "GIT_DIR"
result_out "GIT_DIR for command" "$DEF_GIT_OPTS repack" "$(rgrep)"
result_out "GIT_DIR for find" "$EXPF" "$(fgrep)"

# -------------------- pruneobjectsdir --------------------

orphans=("$GD_PACK/pack-p123456789012345678901234567890123456789.pack")
for e in bitmaps idx keep mtimes rev ; do
	orphans=("${orphans[@]}" "$GD_PACK/pack-0123456789012345678901234567890123456789.$e")
done
for o in "${orphans[@]}" ; do
    touch -d '-1 week' "$o"
done
OUT=$("$TEST_PROG" --git-dir "$GD" repack -A -d 2>&1)
result_success "cleaned orphans"
for o in "${orphans[@]}" ; do
    e=$(ext "$o")
    [ ! -f "$o" ]
    result "cleaned orphan pack- of type $e" "$o"
done

# -------------------- preserve --------------------

before=$(ls "$GD_PACK"/pack-*.{pack,idx})
p_before=$(ls "$GD_PRES"/pack-*.old-{pack,idx} 2>/dev/null)
create_commit
OUT=$("$TEST_PROG" --git-dir "$GD" repack -A -d 2>&1)
result_success "repack -A -d"
after=$(ls "$GD_PACK"/pack-*.{pack,idx})
p_after=$(ls "$GD_PRES"/pack-*.old-{pack,idx})
on_fail=$(echo "Before:" ; echo "$before" ; echo "     After:" ; echo "$after")
[ "$before" != "$after" ]
result "new packs after repack" "$on_fail"
on_fail=$(echo "Before:" ; echo "$p_before" ; echo "     After:" ; echo "$p_after")
[ -n "$(echo "$p_after" | grep -F -f <(echo "$p_before"))" ]
result "new preserved packs after repack" "$on_fail"

# -------------------- prune-preserved --------------------

p_before=$(ls "$GD_PRES"/pack-*.old-{pack,idx} 2>/dev/null)
OUT=$("$TEST_PROG" --git-dir "$GD" -c repack.preservedPruneExpire="2 hours ago" prune-preserved 2>/dev/null)
result_success "prune-preserved 2 hours ago"
p_after=$(ls "$GD_PRES"/pack-*.old-{pack,idx} 2>/dev/null)
result_out "$RESULT_NAME" "$p_before" "$p_after"

p_before=$(ls "$GD_PRES"/pack-*.old-{pack,idx} 2>/dev/null)
OUT=$("$TEST_PROG" --git-dir "$GD" -c repack.preservedPruneExpire="invalid" prune-preserved 2>/dev/null)
result_error "prune-preserved invalid expiry"
p_after=$(ls "$GD_PRES"/pack-*.old-{pack,idx} 2>/dev/null)
result_out "$RESULT_NAME" "$p_before" "$p_after"

OUT=$("$TEST_PROG" --git-dir "$GD" prune-preserved 2>/dev/null)
result_success "prune-preserved"
p_after=$(ls "$GD_PRES"/pack-*.old-{pack,idx} 2>/dev/null)
result_out "$RESULT_NAME" "" "$p_after"

# -------------------- delta-islands --------------------

OUT=$("$TEST_PROG" -n -c repack.cloneDeltaIsland=false repack)
result_success "simple repack"
OUT=$(echo "$OUT" | grep "cmd: git .* repack")
result_out "$RESULT_NAME" "  cmd: git -c repack.cloneDeltaIsland=false repack" "$OUT"

# -------------------- exit codes --------------------

result_successes
result_errors

exit $RESULT
