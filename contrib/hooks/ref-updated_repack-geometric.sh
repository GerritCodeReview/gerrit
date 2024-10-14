#!/bin/bash -e
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

#
# Best run from the Gerrit ref-updated hook
#

# Make a simple "least effort" attempt to run geometric repacking after every
# known update which may have written git objects, all while avoiding overloading
# a server with too much repacking work.

# The least effort avoids running more than one git repack on the same repo at a
# time, or while a git gc is already running on a repo (by using .git/gc.pid as
# a lock). To avoid overloading the server, it also avoids running more than 3
# git repacks total across all repos. If any of these conditions would be violated,
# this script simply does nothing and exits. The intention is to avoid doing too
# much work during a burst, assuming that future updates will likely be good enough
# to service the repos which were missed.
#
# Since this is an event based approach to repository maintenance, it is
# recommended that another time based GC approach, perhaps a more significant and
# costly one, repacking refs, creating bitmaps... be used in parallel with this
# script. This simple policy of "least effort" should keep most repos from
# degrading much even with very infrequent time based GCs.
#
# Since this script uses gc.pid to lock the repo against other git gcs, it means
# that this script could potentially starve any time based gc maintenance from
# happening on busy repos. It is therefore advisable for any such time based gc
# jobs to spin for a while attempting to run if the job cannot acquire the gc.pid
# lock to help ensure that time based gc also gets a chance to run.
#
# In order to be able to skip repacking for each update happening during repacking,
# this script returns immediately after starting repacking in the background. If
# this script were to instead block during repacking, it would simply delay
# repacking for those updates instead of having a consolidating effect. That being
# said, a smarter script might consider tracking that some updates happened after
# repacking started and ensure that it gets repacked once again (while still
# consolidating many updates), but that would likely no longer qualify as least
# effort.
#

[ -z "$GERRIT_SITE" ] && { echo "ERROR: GERRIT_SITE not set" ; exit 1 ; }
[ -z "$GIT_DIR" ] && { echo "ERROR: GIT_DIR not set" ; exit 2 ; }

# ---- Generic ----

debug() { true || echo "---- debug: $@" ; }

cleanup() { [ -n "$GC_LOCK" ] && rm -- "$GCLOCK" ; }

exec_locked() { # <lock> <cmd> [<args>...]
    local lock=$1 rtn=0
    shift
    if ( set -o noclobber ; echo $$ > "$lock" ) > /dev/null 2>&1 ; then
        GC_LOCK=$lock
        debug "locked $lock"
        "$@" || rtn=$?
        rm -- "$lock" && unset GC_LOCK
        debug "unlocked $lock"
        return $rtn
    fi
    debug "already locked $lock"
    return 20
}

exec_acquired() { # <lock> <max> <cmd> [<args>...]
    local semaphore=$1 max=$2 rtn=0 slot lock
    shift 2
    mkdir -p -- "$semaphore"
    for slot in $(seq "$max") ; do
        lock="$semaphore/$slot"
        touch -- "$lock"
        exec 3<> "$lock"
        if flock -n 3 ; then
            debug "acquired semaphore $slot"
            "$@" || rtn=$?
            flock -o 3
            debug "released semaphore $slot"
            return $rtn
        fi
    done
    debug "semaphore loaded $semaphore"
    return 30
}

# ---- Policy ----

gc_lock() { # <cmd> [<args>...]
    exec_locked "$LOCK" "$@"
}

gc_runner() { # <cmd> [<args>...]
    exec_acquired "$SEMAPHORE" "$MAX_RUNNERS" "$@"
}

trap cleanup EXIT

MAX_RUNNERS=3
SEMAPHORE=$GERRIT_SITE/logs/git-geometric.semaphore
LOCK=$GIT_DIR/gc.pid

gc_runner gc_lock git repack -n -d --no-write-bitmap-index --geometric=2 &

