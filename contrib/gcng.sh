#!/bin/bash -e
# Copyright (C) 2011, 2020 SAP SE
# Copyright (C) 2024 NVIDIA
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
# Git GC Next Generation
#

MYPROG=$(readlink -f -- "$BASH_SOURCE")
MYNAME=$(basename -- "$MYPROG")

Usage() { # [--configs]
    cat - <<EOF
  $MYNAME [<git-options>...] [-n] gc|repack|{gcng-step,...} [<cmd-options-args...>]
  $MYNAME -u|-h|--help [--configs]
  $MYNAME --configs

  Run advanced cleanup (gc) on a git repo

  git-options:

  Any git option normally passed before the git command. These will be passed
  onto the command and will also be used by $MYNAME to configure it. Example
  git-options which this program will use to affect its behavior:

  [--git-dir=<path>|--git-dir <path>]
  [-c <name>=<value>]

  cmd-options-args:

  Any options or arguments normally passed after the git command, will be passed
  onto the command.

  other options:

  -n|--dry-run  Do a dry-run showing what would be done
  -u|-h|--help  Print this help message
  --configs     Print the config options this program supports.

  This program is intended to be a drop in replacement for git gc. Under
  the hood it either calls git gc or git repack after running many maintenance
  steps, or it will run the specified maintenance steps only. The maintenance
  steps do many of the things that git repos need to avoid manual intervention
  and cleanup in order to keep functioning efficiently. When specifying a list
  of steps to run, only the last specified step may have arguments passed to it.

  The high level $MYNAME maintenance steps which can be run individually are:
       git-gc           normal git gc
       git-repack       git repack wrapped with a gc lock
       prune-refsdir    prune the refs directory
       prune-objectsdir prune the objects directory
       prune-preserved  prune the objects/pack/preserved directory
       preserve         preserve the current packs and their indexes

EOF

    while [ $# -gt 0 ] ; do
        case "$1" in
            --configs) configs "$@" ;;
        esac
        [ $# -gt 0 ] && shift
    done
}

configs() {
    cat - <<EOF
 $MYNAME supports the following git config options:

 gc.preserveoldpacks (enabled by default)
               Include the 'preserve' in the list of default steps to run when using
               the gc or repack mode (when not specifying individual steps).

 repack.locker locker program to wrap repacking with. '<locker> lock <path> <pid>'
               will be called before repacking with a path of \$GIT_DIR/gc.pid,
               and the $MYNAME process id. git repack will then only be run if the
               locker 'lock' call exits with a zero exit status. 'locker unlock
               <path> <pid>' will be called (with the same args as the 'lock' call)
               after git repack completes.

               This can be used to call another locker than the simple pid file
               locker. For example: flock could be used to avoid stale locks locally,
               and lock_ssh.sh or lock_k8s.sh (see https://github.com/quic/lockers.git)
               could be used to avoid stale locks in a cluster.

 repack.preservedPruneExpire
               older preserved packs(.old-pack) and indices(.old-idx) than this will
               be pruned when the prune-preserved step runs.

 repack.cloneDeltaIsland (enabled by default)
               Create a delta island with refs/heads and refs/tags in it so that a
               default clone does not rely on deltas outside of what will be sent
               to clients.
EOF
}

in_args() { # <element> [<value>...
  local a e=$1 ; shift
  for a ; do
      [ "$a" = "$e" ] && return 0
  done
  return 1
}

error() { echo "ERROR: $1" >&2 ; [ -n "$2" ] && exit $2 ; exit 99 ; }

ext() { echo "${1##*\.}" ; } # <filename>.<ext> > <ext>

is_orphan_pack() { # pack-<name>
    local peer=pack ext=$(ext "$1") dir=$(dirname -- "$1")/
    [ "$ext" = "pack" ] && peer=idx
    [ ! -f "$dir$(basename -- "$1" "$ext")$peer" ]
}

get_default() { # <key> -> <value>
    case "$1" in
        repack.locker) echo "lock_pidfile" ;;
        repack.cloneDeltaIsland) echo true ;;
        gc.preserveoldpacks) echo true ;;
    esac
}

get() { # <key> > <value>
    local key=$1
    local val=${CFGS["$1"]}
    [ -z "$val" ] && val=$(git config -- "$key") # many gits not new enough for 'get'
    [ -z "$val" ] && val=$(get_default "$key")
    echo "$val"
}

get_expiry_newer() { # <key> > [-newermt @timestamp] # blank if expiry not set
    local ts=$(get "$1") newermt key section
    [ -z "$ts" ] && return

    # Split cannot yet handle a key with more than one dot in it. Probably best to
    # use git to do this part eventually, ideally if it supported:
    #     git config -c <key>-<value> ...
    # Another solution would be if git config supported writing to stdout.
    # If supporting more than one dot is needed before that, make git config write
    # to a temp file, but that's ugly.
    IFS=. read key section < <(echo "$1") || true
    newermt=$({ echo "[$key]"; echo "$section = $ts" ; } | \
            git config -f - --type expiry-date "$key.$section") || return
    echo "! -newermt @$newermt"
}

is_enabled() { # <key>
    local tf=$(get "$1") key section
    if [ "$tf" = "1" ] || [ "$tf" = "true" ] || [ "$tf" = "enabled" ] ; then
        return 0
    fi
    if [ -z "$tf" ] || [ "$tf" = "0" ] || [ "$tf" = "false" ] || \
        [ "$tf" = "disabled" ] ; then
        return 1
    fi
    return 1
}

c_opt() { # <key>=<value>
    GIT_OPTS=("${GIT_OPTS[@]}" -c "$1")
    local key val
    IFS== read key val < <(echo "$1") || true
    CFGS["$key"]=$val
}

set_default_git_opts() {
    local cloneDeltaIsland=$(get repack.cloneDeltaIsland)
    if [ "$cloneDeltaIsland" = "true" ] ; then
        GIT_OPTS=("${GIT_OPTS[@]}" -c pack.island=refs/heads/ -c pack.island=refs/tags/)
    fi
}

e() {
    "$DRY_RUN" || echo "......  $(date) ......"
    echo "  cmd: $@"
    if ! "$DRY_RUN" ; then
        if ! "$@" ; then
            EXIT_CODE=$?
            echo "ERROR CODE: $EXIT_CODE"
        fi
    fi
}

egit() { e git "${GIT_OPTS[@]}" "$@" ; } # (external git) <cmd> [<args>...]

on_exit_work() { unlock_repack ; }

on_exit() { local rtn=$? ; on_exit_work && return $rtn ; }

pidfile_lock() { # <lock_path> <pid>
    ( set -o noclobber ; echo "$2" > "$1" ) > /dev/null 2>&1 && return
    return 20 # not zero, and consistent with the locks in quic/lockers.git
}

pidfile_unlock() { [ "$(< "$1")" = "$2" ] && rm -- "$1" && true ; } # <lock_path> <pid>

lock_pidfile() { pidfile_"$1" "$2" "$3" ; } # [un]lock <lock_path> <pid>

lock_repack() {
    "${REPACK_LOCKER[@]}" lock "$GC_PID" $$ && return
    local rtn=$?
    echo "WARNING: skipping repacking, already locked($rtn) $GC_PID." >&2
    return $rtn
}

unlock_repack() {
    $REPACK_LOCKED && "${REPACK_LOCKER[@]}" unlock "$GC_PID" $$ || true
}

prune-refsdir() {
    e find "$GIT_DIR/refs" -mindepth 2 -type d -empty -mmin +60 -delete
    # ToDo: locked refs, zero length refs
}

prune-objectsdir() {
    e find "$GIT_DIR/objects" -maxdepth 1 -type f \
            \( -name 'incoming_*.pack' -o -name 'incoming_*.idx' \) \
            -mtime +1 -delete

    e find "$GIT_PACK_DIR" -maxdepth 1 -type f \
            \( -name 'tmp_pack_*' -o -name 'tmp_idx_*' -o -name 'tmp_mtimes_*' \) \
            -mtime +1 -delete

    e find "$GIT_PACK_DIR" -maxdepth 1 -type f \
            -name 'pack-*.keep' -mmin +720 \
            -execdir grep -q '^jgit receive-pack from ' '{}' ';' \
            -delete

    e find "$GIT_PACK_DIR" -maxdepth 1 -name 'pack-*.*' \
            \( -name '*.bitmaps' -o -name '*.idx' -o -name '*.keep' \
            -o -name '*.mtimes' -o -name '*.pack' -o -name '*.rev' \) \
            -type f -mtime +1 \
            -execdir "$MYPROG" --callback is_orphan_pack '{}' \; \
            -delete
}

ext() { echo "${1##*\.}" ; } # <filename>.<ext> > <ext>

# pack-0...2.pack to pack-0...2.old-pack
# pack-0...2.idx to pack-0...2.old-idx
preserved_name() { # <packfile_path> > <preserved_packfile_name>
    local ext=$(ext "$1")
    echo "$(basename -- "$1" ".$ext").old-$ext"
}

preserve() {
    local rtn=0 i=0 E=e file
    e mkdir -p -- "$GIT_PRESERVED_DIR"
    for file in "$GIT_PACK_DIR"/pack-*.{pack,idx} ; do
        i=$(($i + 1)) ; [ "$i" -gt 3 ] && E=''
        $E ln -f -- "$file" "$GIT_PRESERVED_DIR/$(preserved_name "$file")" || rtn=$?
    done
    [ -z "$E" ] && echo "... $(($i - 3)) more silenced preserves..."
    return $rtn
}

prune-preserved() {
    local expiry ; expiry=$(get_expiry_newer repack.preservedPruneExpire) || return
    [ -d "$GIT_PRESERVED_DIR" ] || return 0
    e find "$GIT_PRESERVED_DIR" -maxdepth 1 -name 'pack-*.*' \
            \( -name '*.old-idx' -o -name '*.old-pack' \) \
            $expiry -delete
}

git-repack() {
    lock_repack || return
    REPACK_LOCKED=true
    egit repack "${CMD_OPTS[@]}"
    unlock_repack
    REPACK_LOCKED=false
}

step() {
    echo "=============== Step: $1 ==============="
    if [ "$1" = "git-gc" ] ; then
        egit gc "${CMD_OPTS[@]}"
    elif in_args "$1" "${VALID_STEPS[@]}" ; then
        "$1"
    fi
}

set_steps() {
    if in_args "$1" gc repack ; then
        local preserve=''
        is_enabled gc.preserveoldpacks && preserve=preserve
        STEPS=(prune-refsdir prune-objectsdir prune-preserved $preserve "git-$1")
        return
    fi

    local step
    IFS=, read -ra STEPS <<< "$1"
    for step in "${STEPS[@]}" ; do
        if ! in_args "$step" "${VALID_STEPS[@]}" ; then
            error "Invalid argument: $step" $INVALID_ARGUMENT
        fi
    done
}

INVALID_ARGUMENT=1

EXIT_CODE=0
DRY_RUN=false
STEPS=usage
unset CFGS GIT_OPTS
declare -A CFGS
VALID_STEPS=(git-gc git-repack prune-refsdir prune-objectsdir prune-preserved preserve)
while [ $# -gt 0 ] ; do
    case "$1" in
        -u|-h|--help) shift ; Usage "$@" ; exit ;;
        --configs) configs ; exit ;;
        -n|--dry-run) DRY_RUN=true ;;
        --callback) shift ; "$@" ; exit ;;

        --git-dir) GIT_OPTS=("${GIT_OPTS[@]}" "$1" "$2") ; shift ;;
        --git-dir=*) GIT_OPTS=("${GIT_OPTS[@]}" "$1") ;;
        -c) shift ; c_opt "$1" ;;

        --) shift ; break ;;
        *)  set_steps "$1" ; shift ; CMD_OPTS=("$@") ; break ;;
    esac
    [ $# -gt 0 ] && shift
done
[ "$STEPS" = "usage" ] && { Usage ; exit ; }

GIT_DIR=$(git "${GIT_OPTS[@]}" rev-parse --git-dir) # may be relative!
GIT_PACK_DIR=$GIT_DIR/objects/pack
GIT_PRESERVED_DIR=$GIT_PACK_DIR/preserved
GC_PID=$GIT_DIR/gc.pid
REPACK_LOCKER=($(get repack.locker))
REPACK_LOCKED=false
set_default_git_opts
trap on_exit EXIT

echo "Running Next Gen Git GC on repo: $(readlink -f -- "$GIT_DIR")"
echo
echo "Executing:"
for step in "${STEPS[@]}" ; do
    step "$step"
done
echo ".............................................."
exit $EXIT_CODE
