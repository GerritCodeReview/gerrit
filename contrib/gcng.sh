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

shopt -s nullglob

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

 refs.locksPuneExpire (defaults to 2 days ago)
               older ref locks and ref log locks than this will be pruned as part of
               the prune-refsdir step.

 repack.cloneDeltaIsland (enabled by default)
               Create a delta island with refs/heads and refs/tags in it so that a
               default clone does not rely on deltas outside of what will be sent
               to clients.

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

 repack.lockPruneExpire (defaults to "12 hours ago")
               older gc.pid files (or directories) than this will be pruned before
               attempting to lock gc.pid.

 repack.detached (disabled by default)
               Create a local copy of the repo and run repacking on the copy instead
               of on the original repo. After successful repack completion, copy in
               any remaining pack_* files and loose objects from the copied/repacked
               repo to the original repo and deleting any pack_* files and loose
               objects in the original repo which were present right after the copy
               and which are no longer present in the copied/repacked repo.
               Hardlinking is used when possible to make copies of everything under
               the objects directory.

               This mode allows repacking to run on a quiescent copy of the repo, and
               since this is "gc.pid lockless" it is useful to enable faster geometric
               repacking to potentially continue to run on the original repo without
               interferring with the detached repacking.

 repack.objectsTmpPruneExpire (defaults to 2 days ago)
               older temporary files in the objects directories than this will
               be pruned when the prune-objects step runs.

 repack.objectsEmptyPruneExpire (defaults to 2 days ago)
               zero-size files in the objects directory older than this will be
               pruned when the prune-objects step runs.

 repack.preservedPruneExpire
               older preserved packs(.old-pack) and indices(.old-idx) than this will
               be pruned when the prune-preserved step runs.
EOF
}

HEX='[0-9a-f]'
HEX10=$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX
HEX38=$HEX10$HEX10$HEX10$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX

# ----- simple generic -----

in_args() { # <element> [<value>...
  local a e=$1 ; shift
  for a ; do
      [ "$a" = "$e" ] && return 0
  done
  return 1
}

exclude_args() { # <element_to_exclude> [<args>] # sets REPLY array
    local a exclude=$1 ; shift
    REPLY=()
    for a in "$@" ; do
        [ "$a" = "$exclude" ] || REPLY+=("$a")
    done
}

existing_paths() { # [paths...] -> sets REPLY[@] to existing paths
    local path
    REPLY=()
    for path in "$@" ; do
        [[ ! -e "$path" ]] || REPLY+=("$path")
    done
}

error() { echo "ERROR: $1" >&2 ; [ -n "$2" ] && exit $2 ; exit 99 ; }

set_status() { return $1 ; }

ext() { echo "${1##*\.}" ; } # <filename>.<ext> > <ext>

# ----- git simple generic -----

PRINT0='' ; Z0='' ; X0=''
# <path_to_cd_to> > loose_objects_0
find_loose() { (cd "$1" ; find . -maxdepth 2 -type f -path "./$HEX$HEX/$HEX38" $PRINT0 | sort $Z0) ; }
# <path_to_cd_to> > packs_0
find_packs() { (cd "$1" ; find . -maxdepth 1 -type f -name 'pack-*.*' $PRINT0 | sort $Z0) ; }

is_orphan_pack() { # pack-<name>
    local peer=pack ext=$(ext "$1") dir=$(dirname -- "$1")/
    [ "$ext" = "pack" ] && peer=idx
    [ ! -f "$dir$(basename -- "$1" "$ext")$peer" ]
}

# ----- git config -----

get_default() { # <key> -> <value>
    case "$1" in
        refs.locksPuneExpire) echo "2 days ago" ;;
        repack.cloneDeltaIsland) echo true ;;
        repack.locker) echo "lock_pidfile" ;;
        repack.lockPruneExpire) echo "12 hours ago" ;;
        repack.objectsTmpPruneExpire) echo "2 days ago" ;;
        repack.objectsEmptyPruneExpire) echo "2 days ago" ;;
        repack.detached) echo false ;;
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
    [ -z "$ts" ] || [ "$ts" = "now" ] && return

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

# ----- execution -----

n() { # [- <hidden_cmd>] <cmd> [<args>...]
    "$DRY_RUN" || echo "......  $(date) ......"
    local cmd=("$@")
    [ "$1" = "-" ] && { shift ; cmd=("$@") ; shift ; }
    echo "  cmd: $*"
    "$DRY_RUN" || "${cmd[@]}"
}

exit_code() { # <cmd> [<args>...]
    if "$@" ; then : ; else # avoid negating $?
         EXIT_CODE=$?
         echo "ERROR CODE: $EXIT_CODE"
    fi
}

e() { n - exit_code "$@" ; } # <cmd> [<args>...]

q() { "$@" >/dev/null 2>&1 ; } # <cmd> [<args>...] # quiets stdout and stderr

retry() { local i n=$1 ; shift ; for i in $(seq "$n") ; do "$@" && return ; done ; } # <count> <cmd> [<args>...]

egit() { e git "${GIT_OPTS[@]}" "$@" ; } # (external git) <cmd> [<args>...]

# ----- gc.pid -----

on_exit_work() { unlock_repack ; }

on_exit() { local rtn=$? ; on_exit_work && return $rtn ; }

pidfile_lock() { # <lock_path> <pid>
    ( set -o noclobber ; echo "$2" > "$1" ) > /dev/null 2>&1 && return
    return 20 # not zero, and consistent with the locks in quic/lockers.git
}

pidfile_unlock() { [ "$(< "$1")" = "$2" ] && rm -- "$1" && true ; } # <lock_path> <pid>

lock_pidfile() { pidfile_"$1" "$2" "$3" ; } # [un]lock <lock_path> <pid>

prune-gcpid() {
    local expiry
    if expiry=$(get_expiry_newer repack.lockPruneExpire) ; then
        # Using rm to handle both files and/or directories
        e find "$GIT_DIR" -maxdepth 1 -name 'gc.pid' $expiry \
            -execdir rm -rf -- '{}' ';' || true
    fi
}

lock_repack() {
    prune-gcpid
    "${REPACK_LOCKER[@]}" lock "$GC_PID" $$ && return
    local rtn=$?
    echo "WARNING: skipping repacking, already locked($rtn) $GC_PID." >&2
    return $rtn
}

unlock_repack() {
    $REPACK_LOCKED && "${REPACK_LOCKER[@]}" unlock "$GC_PID" $$ || true
}

# ----- pruning -----

prune-reflock() { # ref_file
    expiry=$(get_expiry_newer refs.locksPuneExpire) || return 0
    [ ! -f "$1.lock" ] || e find "$1.lock" $expiry -delete
}

prune-refsdir() {
    e find "$GIT_DIR/refs" -mindepth 2 -type d -empty -mmin +60 -delete
    expiry=$(get_expiry_newer refs.locksPuneExpire) || return 0
    existing_paths "$GIT_DIR/logs/refs"
    e find "$GIT_DIR/refs" "${REPLY[@]}" -type f -name '*.lock' $expiry -delete
    for ref in HEAD FETCH_HEAD ORIG_HEAD packed-refs ; do
        prune-reflock "$GIT_DIR/$ref"
        prune-reflock "$GIT_DIR/logs/$ref"
    done
    # ToDo: zero length refs
}

prune-objectsdir() {
    local expiry
    if expiry=$(get_expiry_newer repack.objectsTmpPruneExpire) ; then
        e find "$GIT_OBJ_DIR" -maxdepth 1 -type f \
                \( -name 'incoming_*.pack' -o -name 'incoming_*.idx' -o -name 'tmp_object_git2_*' \
                -o -name 'noz*.tmp' \) \
                $expiry -delete

        e find "$GIT_PACK_DIR" -maxdepth 1 -type f \
                \( -name 'tmp_bitmaps_*' -o -name 'tmp_idx_*' -o -name 'tmp_keep_*' \
                -o -name 'tmp_mtimes_*' -o -name 'tmp_pack_*' -o -name 'tmp_rev_*' \) \
                $expiry -delete
    fi

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

    if expiry=$(get_expiry_newer repack.objectsEmptyPruneExpire) ; then
        e find "$GIT_OBJ_DIR" -mindepth 2 -maxdepth 2 -type f -size 0 $expiry -delete
    fi
}

# ----- preserve -----

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

# ----- detached -----

on_exit_detached() {
    local rtn=$?
    rm -rf -- "$DETACHED_DIR"
    return $rtn
}

update_git_dir_option() {
    unset git_opts
    local opt skip=false
    for opt in "${GIT_OPTS[@]}" ; do
        $skip && { skip=false ; continue ; }
        case "$opt" in
            --git-dir) git_opts=("${git_opts[@]}" "$opt" "$DETACHED_DIR") ; skip=true ; continue ;;
            --git-dir=*) opt="--git-dir=$DETACHED_DIR" ;;
        esac
        git_opts=("${git_opts[@]}" "$opt")
    done
    GIT_OPTS=("${git_opts[@]}")
}

create_detached_repo() {
    mkdir -- "$DETACHED_DIR" # intended to fail if it already exits
    retry 5 cp -rlp -t "$DETACHED_DIR" -- "$GIT_OBJ_DIR" # attempt to hardlink these instead of copying
    exclude_args "$GIT_OBJ_DIR" "$GIT_DIR"/*
    retry 5 cp -rnp  -t "$DETACHED_DIR" -- "${REPLY[@]}"
    if [ -f "$GIT_DIR"/packed-refs ] ; then # in case ref-packing was running while above
        retry 5 cp -p -t "$DETACHED_DIR" -- "$GIT_DIR"/packed-refs
    fi
    retry 5 cp -rnlp -t "$DETACHED_DIR" -- "$GIT_OBJ_DIR" # in case new objects referenced while copying refs
    retry 5 cp -rnlp -t "$DETACHED_DIR" -- "$GIT_OBJ_DIR" # in case repacking was running while above
    rm -rf -- "$DETACHED_DIR/gc.pid"
    find_packs "$DETACHED_PACK_DIR" > "$DETACHED_BEFORE"
    find_loose "$DETACHED_OBJ_DIR" > "$DETACHED_BEFORE".loose
}

update_from_detached_repo() {
    comm -13 $Z0 -- "$DETACHED_BEFORE" <(find_packs "$DETACHED_PACK_DIR") | \
            xargs $X0 -i cp -nlp -t "$GIT_PACK_DIR" -- "$DETACHED_PACK_DIR/{}"
    (
        shopt -s nullglob
        local hex2
        for hex2 in "$DETACHED_OBJ_DIR"/$HEX$HEX ; do
            hex2=$(basename -- "$hex2")
            if ! q rmdir -- "$DETACHED_OBJ_DIR/$hex2" ; then
                mkdir -p -- "$GIT_OBJ_DIR/$hex2"
                cp -nlp -t "$GIT_OBJ_DIR/$hex2" -- "$DETACHED_OBJ_DIR/$hex2"/$HEX38
            fi
        done
    )
    comm -23 $Z0 -- "$DETACHED_BEFORE" <(find_packs "$DETACHED_PACK_DIR") | \
            xargs $X0 -i rm -f -- "$GIT_PACK_DIR/{}"
    comm -23 $Z0 -- "$DETACHED_BEFORE".loose <(find_loose "$DETACHED_OBJ_DIR") | \
            xargs $X0 -i rm -f -- "$GIT_OBJ_DIR/{}"
}

detached_repack() {
    echo "detached-repacking"
    DETACHED=.detached-repacking
    DETACHED_DIR=$GIT_DIR/$DETACHED
    DETACHED_OBJ_DIR=$DETACHED_DIR/objects
    DETACHED_PACK_DIR=$DETACHED_OBJ_DIR/pack
    DETACHED_BEFORE=$DETACHED_DIR/.before
    (
        trap on_exit_detached EXIT
        n create_detached_repo
        EXIT_CODE=0
        (
            export GIT_DIR=$DETACHED_DIR
            update_git_dir_option
            egit repack "${CMD_OPTS[@]}"
            set_status $EXIT_CODE
        ) || EXIT_CODE=$?
        [ $EXIT_CODE -eq 0 ] && n update_from_detached_repo
        set_status $EXIT_CODE
    ) || EXIT_CODE=$?
}

# ----- other -----

git-repack() {
    if $(get repack.detached) ; then
        detached_repack
        return
    fi

    lock_repack || return
    REPACK_LOCKED=true
    egit repack "${CMD_OPTS[@]}"
    unlock_repack
    REPACK_LOCKED=false
}

# ----- steps -----

step() {
    echo "=============== Step: $1 ==============="
    if [ "$1" = "git-gc" ] ; then
        prune-gcpid
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

q sort -z <(echo) && q comm -z <(echo) <(echo) && { PRINT0=-print0 ; Z0=-z ; X0=-0 ; }

INVALID_ARGUMENT=1

EXIT_CODE=0
DRY_RUN=false
STEPS=usage
unset CFGS GIT_OPTS CMD_OPTS
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
GIT_OBJ_DIR=$GIT_DIR/objects
GIT_PACK_DIR=$GIT_OBJ_DIR/pack
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
