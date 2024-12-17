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
# Call ideally after every known update which may have written git objects.
#
#
# Make a simple "least effort" attempt to run geometric repacking on an event
# basis, all while avoiding overloading a server with too much repacking work.

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

MYPROG=$(readlink -f -- "$BASH_SOURCE")
MYNAME=$(basename -- "$MYPROG")

# ---- Generic ----

ztime() { date -u +"%Y-%m-%dT%H:%M:%SZ" ; }

debug() { echo "$(ztime) - $PROJECT - $@" >&2 ; }

cleanup() { [ -n "$GC_LOCK" ] && rm -f -- "$GC_LOCK" ; }

mkdir_or_die() { # <directory> <error_message>
    if ! mkdir -p -- "$1" 2> /dev/null ; then
        debug "$2: $1"
        exit 2
    fi
}

exec_locked() { # <lock> <cmd> [<args>...]
    local lock=$1 rtn=0
    shift
    trap cleanup EXIT
    if ( set -o noclobber ; echo $$ > "$lock" ) > /dev/null 2>&1 ; then
        GC_LOCK=$lock
        debug "locked($SLOT) $lock"
	mkdir_or_die "$REPACK_LOGS" "cannot make REPACK_LOGS directory"
	mkdir_or_die "$REPACK_LOG_DIR" "cannot make log directory $REPACK_LOG_DIR"
        echo "----- $(ztime) -----" >> "$REPACK_LOG"
        "$@" >> "$REPACK_LOG" 2>&1 || rtn=$?
        rm -- "$lock" && unset GC_LOCK
        debug "unlocked($SLOT) $lock"
        return $rtn
    fi
    debug "already locked($SLOT) $lock"
    return 20
}

exec_acquired() { # <lock> <max> <cmd> [<args>...]
    local semaphore=$1 max=$2 rtn=0 lock
    shift 2
    mkdir_or_die "$semaphore" "cannot make semaphore directory"
    for SLOT in $(seq "$max") ; do
        lock="$semaphore/$SLOT"
        touch -- "$lock"
        exec 3<> "$lock"
        if flock -n 3 ; then
            debug "acquired semaphore($SLOT) $lock"
            "$@" || rtn=$?
            flock -o 3
            debug "released semaphore($SLOT) $lock"
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

usage() { # [<error_message>]
    cat <<EOF

  $MYNAME [--max-runners <MAX>] [--git-dir <GIT_DIR>] [--semaphore <SEMAPHORE>] [--logs <REPACK_LOGS_ROOT>] [--repos <REPOS_ROOT>]

  Run geometric repacking on a git repository assumed to be part of a greater collection
  of repositories. Skip running if MAX runners are already running across the collection,
  or if the current repository is already locked with gc.pid.

  Options:

  --max-runners  number of git repacks to run concurrently, defaults to 3.
  --git-dir      repo dir to run git repack on, defaults to \$GIT_DIR
  --semaphore    path to semaphore to use to enforce MAX. If unspecified, use
                 REPACK_LOGS/$MYNAME.semaphore. If REPACK_LOGS is also unspecified
                 then use /tmp/$MYNAME.semaphore.
  --logs         path to root directory for logs. git repack will get logged to a separate
                 file for each project under REPACK_LOGS_ROOT. If unspecified, git repack
                 will get logged to GIT_DIR/repack.log.
  --repos        root directory where repositories are stored. Used to determine project
                 name from GIT_DIR which is assumed to be relative to REPOS_ROOT. Project
                 name defaults to ALL_PROJECTS if unspecified.

EOF

    [ -n "$1" ] && echo "ERROR: $1"
    exit 1
}

MAX_RUNNERS=3
unset REPOS REPACK_LOGS REPACK_LOG_DIR SEMAPHORE
while [ $# -gt 0 ] ; do
    case "$1" in
        --max-runners) shift ; MAX_RUNNERS=$1 ;;
        --git-dir) shift ; export GIT_DIR=$1 ;;
        --semaphore) shift ; SEMAPHORE=$1 ;;
        --logs) shift ; REPACK_LOGS=$1 ;;
        --repos) shift ; REPOS=$1 ;;
        *) usage "unknown arg: $1" ;;
    esac
    [ $# -gt 0 ] && shift
done

[ -z "$GIT_DIR" ] && usage "GIT_DIR not set!"
[ -z "$(git rev-parse --git-dir 2> /dev/null)" ] && usage "$GIT_DIR is not a git repository!"

LOCK=$GIT_DIR/gc.pid

if [ -z "$REPOS" ] ; then
    PROJECT=ALL_PROJECTS
else
    PROJECT=$(realpath -m --relative-to "$REPOS" "$GIT_DIR")
    [ -z "$PROJECT" ] && PROJECT=UNKNOWN_PROJECT
fi

REPACK_LOG=$REPACK_LOGS/$PROJECT.log
if [ -z "$REPACK_LOGS" ] ; then
    REPACK_LOGS=$GIT_DIR
    REPACK_LOG=$REPACK_LOGS/repack.log
else
    [ -z "$SEMAPHORE" ] && SEMAPHORE=$REPACK_LOGS/$MYNAME.semaphore
fi
REPACK_LOG_DIR=$(dirname -- "$REPACK_LOG")

[ -z "$SEMAPHORE" ] && SEMAPHORE=/tmp/$MYNAME.semaphore

debug "GIT_DIR:$GIT_DIR"
gc_runner gc_lock git repack -n -d --no-write-bitmap-index --geometric=2 &
