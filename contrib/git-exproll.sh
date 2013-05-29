#!/bin/bash
# Copyright (c) 2012, Code Aurora Forum. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#    # Redistributions of source code must retain the above copyright
#       notice, this list of conditions and the following disclaimer.
#    # Redistributions in binary form must reproduce the above
#       copyright notice, this list of conditions and the following
#       disclaimer in the documentation and/or other materials provided
#       with the distribution.
#    # Neither the name of Code Aurora Forum, Inc. nor the names of its
#       contributors may be used to endorse or promote products derived
#       from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
# ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
# BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
# BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
# OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
# IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

usage() { # error_message

    cat <<-EOF
		usage: $(basename $0) [-unvt] [--noref] [--nolosse] [-r|--ratio number]
		                      [git gc option...] git.repo

		-u|-h                usage/help
		-v verbose
		-n dry-run           don't actually repack anything
		-t touch             treat repo as if it had been touched
		--noref              avoid extra ref packing timestamp checking
		--noloose            do not run just because there are loose object dirs
		                     (repacking may still run if they are referenced)
		-r ratio <number>    packfile ratio to aim for (default 10)

		git gc option        will be passed as args to git gc

		git.repo             to run gc against

		Garbage collect using a pseudo logarithmic packfile maintenance
		approach.  This approach attempts to minimize packfile churn
		by keeping several generations of varying sized packfiles around
		and only consolidating packfiles (or loose objects) which are
		either new packfiles, or packfiles close to the same size as
		another packfile.

		An estimate is used to predict when rollups (one consolidation
		would cause another consolidation) would occur so that this
		rollup can be done all at once via a single repack.  This reduces
		both the runtime and the pack file churn in rollup cases.

		Approach: plan each consolidation by creating a table like this:

		Id Keep Size           Sha1(or consolidation list)      Actions(repack down up note)
		1     - 11356          9052edfb7392646cd4e5f362b953675985f01f96 y - - New
		2     - 429088         010904d5c11cd26a79fda91b01ab454d1001b402 y - - New
		c1    - 440444         [1,2]                                    - - -

		Id:    numbers preceded by a c are estimated "c pack" files
		Keep:  - none, k private keep, o our keep
		Size:  in disk blocks (default du output)
		Sha1:  of packfile, or consolidation list of packfile ids
		Actions
		repack: - n no, y yes
		down:   - noop, ^ consolidate with a file above
		up:     - noop, v consolidate with a file below
		note:   Human description of script decisions:
		         New (file is a new packfile)
		         Consolidate with:<list of packfile ids>
		         (too far from:<list of packfile ids>)

		On the first pass, always consolidate any new packfiles along
		with loose objects and along with any packfiles which are within
		the ratio size of their predecessors (note, the list is ordered
		by increasing size).  After each consolidation, insert a fake
		consolidation, or "c pack", to naively represent the size and
		ordered positioning of the anticipated new consolidated pack.
		Every time a new pack is planned, rescan the list in case the
		new "c pack" would cause more consolidation...

		Once the packfiles which need consolidation are determined, the
		packfiles which will not be consolidated are marked with a .keep
		file, and those which will be consolidated will have their .keep
		removed if they have one.  Thus, the packfiles with a .keep will
		not get repacked.

		Packfile consolidation is determined by the --ratio parameter
		(default is 10).  This ratio is somewhat of a tradeoff.  The
		smaller the number, the more packfiles will be kept on average;
		this increases disk utilization somewhat.  However, a larger
		ratio causes greater churn and may increase disk utilization due
		to deleted packfiles not being reclaimed since they may still be
		kept open by long running applications such as Gerrit.  Sane
		ratio values are probably between 2 and 10.  Since most
		consolidations actually end up smaller than the estimated
		consolidated packfile size (due to compression), the true ratio
		achieved will likely be 1 to 2 greater than the target ratio.
		The smaller the target ratio, the greater this discrepancy.

		Finally, attempt to skip garbage collection entirely on untouched
		repos.  In order to determine if a repo has been touched, use the
		timestamp on the script's keep files, if any relevant file/dir
		is newer than a keep marker file, assume that the repo has been
		touched and gc needs to run.  Also assume gc needs to run whenever
		there are loose object dirs since they may contain untouched
		unreferenced loose objects which need to be pruned (once they
		expire).

		In order to allow the keep files to be an effective timestamp
		marker to detect relevant changes in a repo since the last run,
		all relevant files and directories which may be modified during a
		gc run (even during a noop gc run), must have their timestamps
		reset to the same time as the keep files or gc will always run
		even on untouched repos.  The relevant files/dirs are all those
		files and directories which garbage collection, object packing,
		ref packing and pruning might change during noop actions.
EOF

    [ -n "$1" ] && info "ERROR $1"

    exit 128
}

debug() { [ -n "$SW_V" ] && info "$1" ; }
info() { echo "$1" >&2 ; }

array_copy() { #v2 # array_src array_dst
    local src=$1 dst=$2
    local s i=0
    eval s=\${#$src[@]}
    while [ $i -lt $s ] ; do
        eval $dst[$i]=\"\${$src[$i]}\"
        i=$(($i + 1))
    done
}

array_equals() { #v2 # array_name [vals...]
    local a=$1 ; shift
    local s=0 t=() val
    array_copy "$a" t
    for s in "${!t[@]}" ; do s=$((s+1)) ; done
    [ "$s" -ne "$#" ] && return 1
    for val in "${t[@]}" ; do
        [ "$val" = "$1" ] || return 2
        shift
    done
    return 0
}

packs_sizes() { # git.repo > "size pack"...
    du -s "$1"/objects/pack/pack-$SHA1.pack | sort -n 2> /dev/null
}

is_ourkeep() { grep -q "$KEEP" "$1" 2> /dev/null ; } # keep
has_ourkeep() { is_ourkeep "$(keep_for "$1")" ; } # pack
has_keep() { [ -f "$(keep_for "$1")" ] ; } # pack
is_repo() { [ -d "$1/objects" ] && [ -d "$1/refs/heads" ] ; } # git.repo

keep() { # pack   # returns true if we added our keep
    keep=$(keep_for "$1")
    [ -f "$keep" ] && return 1
    echo "$KEEP" > "$keep"
    return 0
}

keep_for() { # packfile > keepfile
    local keep=$(echo "$1" | sed -es'/\.pack$/.keep/')
    [ "${keep/.keep}" = "$keep" ] && return 1
    echo "$keep"
}

idx_for() { # packfile > idxfile
    local idx=$(echo "$1" | sed -es'/\.pack$/.idx/')
    [ "${idx/.idx}" = "$idx" ] && return 1
    echo "$idx"
}

# pack_or_keep_file > sha
sha_for() { echo "$1" | sed -es'|\(.*/\)*pack-\([^.]*\)\..*$|\2|' ; }

private_keeps() { # git.repo -> sets pkeeps
    local repo=$1 ary=$2
    local keep keeps=("$repo"/objects/pack/pack-$SHA1.keep)
    pkeeps=()
    for keep in "${keeps[@]}" ; do
        is_ourkeep "$keep" || pkeeps=("${pkeeps[@]}" "$keep")
    done
}

is_tooclose() { [ "$(($1 * $RATIO))" -gt "$2" ] ; } # smaller larger

unique() { # [args...] > unique_words
    local lines=$(while [ $# -gt 0 ] ; do echo "$1" ; shift ; done)
    lines=$(echo "$lines" | sort -u)
    echo $lines  # as words
}

outfs() { # fs [args...] > argfs...
    local fs=$1 ; shift
    [ $# -gt 0 ] && echo -n "$1" ; shift
    while [ $# -gt 0 ] ; do echo -n "$fs$1" ; shift ; done
}

sort_list() { # < list > formatted_list
    # n has_keep size sha repack down up note
    awk '{ note=$8; for(i=8;i<NF;i++) note=note " "$(i+1)
           printf("%-5s %s %-14s %-40s %s %s %s %s\n", \
                     $1,$2,   $3,  $4, $5,$6,$7,note)}' |\
        sort -k 3,3n -k 1,1n
}

is_touched() { # git.repo
    local repo=$1
    local loose keep ours newer
    [ -n "$SW_T" ] && { debug "$SW_T -> treat as touched" ; return 0 ; }

    if [ -z "$SW_LOOSE" ] ; then
        # If there are loose objects, they may need to be pruned,
        # run even if nothing has really been touched.
        loose=$(find "$repo/objects" -type d \
                      -wholename "$repo/objects/[0-9][0-9]"
                      -print -quit 2>/dev/null)
        [ -n "$loose" ] && { info "There are loose object directories" ; return 0 ; }
    fi

    # If we don't have a keep, the current packfiles may not have been
    # compressed with the current gc policy (gc may never have been run),
    # so run at least once to repack everything.  Also, we need a marker
    # file for timestamp tracking (a dir needs to detect changes within
    # it, so it cannot be a marker) and our keeps are something we control,
    # use them.
    for keep in "$repo"/objects/pack/pack-$SHA1.keep ; do
        is_ourkeep "$keep" && { ours=$keep ; break ; }
    done
    [ -z "$ours" ] && { info 'We have no keep (we have never run?): run' ; return 0 ; }

    debug "Our timestamp keep: $ours"
    # The wholename stuff seems to get touched by a noop git gc
    newer=$(find "$repo/objects" "$repo/refs" "$repo/packed-refs" \
                  '!' -wholename "$repo/objects/info" \
                  '!' -wholename "$repo/objects/info/*" \
                  -newer "$ours" \
                  -print -quit 2>/dev/null)
    [ -z "$newer" ] && return 1

    info "Touched since last run: $newer"
    return 0
}

touch_refs() { # git.repo start_date refs
    local repo=$1 start_date=$2 refs=$3
    (
        debug "Setting start date($start_date) on unpacked refs:"
        debug "$refs"
        cd "$repo/refs" || return
        # safe to assume no newlines in a ref name
        echo "$refs" | xargs -d '\n' -n 1 touch -c -d "$start_date"
    )
}

set_start_date() { # git.repo start_date refs refdirs packedrefs [packs]
    local repo=$1 start_date=$2 refs=$3 refdirs=$4 packedrefs=$5 ; shift 5
    local pack keep idx repacked

    # This stuff is touched during object packs
    while [ $# -gt 0 ] ; do
        pack=$1 ; shift
        keep="$(keep_for "$pack")"
        idx="$(idx_for "$pack")"
        touch -c -d "$start_date" "$pack" "$keep" "$idx"
        debug "Setting start date on: $pack $keep $idx"
    done
    # This will prevent us from detecting any deletes in the pack dir
    # since gc ran, except for private keeps which we are checking
    # manually.  But there really shouldn't be any other relevant deletes
    # in this dir which should cause us to rerun next time, deleting a
    # pack or index file by anything but gc would be bad!
    debug "Setting start date on pack dir: $start_date"
    touch -c -d "$start_date" "$repo/objects/pack"


    if [ -z "$SW_REFS" ] ; then
        repacked=$(find "$repo/packed-refs" -newer "$repo/objects/pack"
                      -print -quit 2>/dev/null)
        if [ -n "$repacked" ] ; then
            # The ref dirs and packed-ref files seem to get touched even on
            # a noop refpacking
            debug "Setting start date on packed-refs"
            touch -c -d "$start_date" "$repo/packed-refs"
            touch_refs "$repo" "$start_date" "$refdirs"

            # A ref repack does not imply a ref change, but since it is
            # hard to tell, simply assume so
            if [ "$refs" != "$(cd "$repo/refs" ; find -depth)" ] || \
               [ "$packedrefs" != "$(<"$repo/packed-refs")" ] ; then
                # We retouch if needed (instead of simply checking then
                # touching) to avoid a race between the check and the set.
                debug "  but refs actually got packed, so retouch packed-refs"
                touch -c "$repo/packed-refs"
            fi
        fi
    fi
}

note_consolidate() { # note entry > note (no duplicated consolidated entries)
    local note=$1 entry=$2
    local entries=() ifs=$IFS
    if  echo "$note" | grep -q 'Consolidate with:[0-9,c]' ; then
        IFS=,
        entries=( $(echo "$note" | sed -es'/^.*Consolidate with:\([0-9,c]*\).*$/\1/') )
        note=( $(echo "$note" | sed -es'/Consolidate with:[0-9,c]*//') )
        IFS=$ifs
    fi
    entries=( $(unique "${entries[@]}" "$entry") )
    echo "$note Consolidate with:$(outfs , "${entries[@]}")"
}

note_toofar() { # note entry > note (no duplicated "too far" entries)
    local note=$1 entry=$2
    local entries=() ifs=$IFS
    if  echo "$note" | grep -q '(too far from:[0-9,c]*)' ; then
        IFS=,
        entries=( $(echo "$note" | sed -es'/^.*(too far from:\([0-9,c]*\)).*$/\1/') )
        note=( $(echo "$note" | sed -es'/(too far from:[0-9,c]*)//') )
        IFS=$ifs
    fi
    entries=( $(unique "${entries[@]}" "$entry") )
    echo "$note (too far from:$(outfs , "${entries[@]}"))"
}

last_entry() { # isRepack pline repackline > last_rows_entry
    local size_hit=$1 pline=$2 repackline=$3
    if [ -n "$pline" ] ; then
        if [ -n "$size_hit" ] ; then
            echo "$repack_line"
        else
            echo "$pline"
        fi
    fi
}

init_list() { # git.repo > shortlist
    local repo=$1
    local file
    local n has_keep size sha repack

    packs_sizes "$1" | {
        while read size file ; do
            n=$((n+1))
            repack=n
            has_keep=-
            if has_keep "$file" ; then
                has_keep=k
                has_ourkeep "$file" && has_keep=o
            fi
            sha=$(sha_for "$file")
            echo "$n $has_keep $size $sha $repack"
        done
    } | sort_list
}

consolidate_list() { # run < list > list
    local run=$1
    local sum=0 psize=0 sum_size=0 size_hit pn clist pline repackline
    local n has_keep size sha repack down up note

    {
        while read n has_keep size sha repack down up note; do
            [ -z "$up" ] && up='-'
            [ -z "$down" ] && down="-"

            if [ "$has_keep" = "k" ] ; then
                echo "$n $has_keep $size $sha $repack - - Private"
                continue
            fi

            if [ "$repack" = "n" ] ; then
                if is_tooclose $psize $size ; then
                    size_hit=y
                    repack=y
                    sum=$(($sum + $sum_size + $size))
                    sum_size=0 # Prevents double summing this entry
                    clist=($(unique "${clist[@]}" $pn $n))
                    down="^"
                    [ "$has_keep" = "-" ] && note="$note New +"
                    note=$(note_consolidate "$note" "$pn")
                elif [ "$has_keep" = "-" ] ; then
                    repack=y
                    sum=$(($sum + $size))
                    sum_size=0 # Prevents double summing this entry
                    clist=($(unique "${clist[@]}" $n))
                    note="$note New"
                elif [ $psize -ne 0 ] ; then
                    sum_size=$size
                    down="!"
                    note=$(note_toofar "$note" "$pn")
                else
                    sum_size=$size
                fi
            else
                sum_size=$size
            fi

            # By preventing "c files" (consolidated) from being marked
            # "repack" they won't get keeps
            repack2=y
            [ "${n/c}" != "$n" ] && { repack=- ; repack2=- ; }

            last_entry "$size_hit" "$pline" "$repack_line"
            # Delay the printout until we know whether we are
            # being consolidated with the entry following us
            # (we won't know until the next iteration).
            # size_hit is used to determine which of the lines
            # below will actually get printed above on the next
            # iteration.
            pline="$n $has_keep $size $sha $repack $down $up $note"
            repack_line="$n $has_keep $size $sha $repack2 $down v $note"

            pn=$n ; psize=$size # previous entry data
            size_hit='' # will not be consolidated up

        done
        last_entry "$size_hit" "$pline" "$repack_line"

        [ $sum -gt 0 ] && echo "c$run - $sum [$(outfs , "${clist[@]}")] - - -"

    } | sort_list
}

process_list() { # git.repo > list
    local list=$(init_list "$1")  plist run=0

    while true ; do
        plist=$list
        run=$((run +1))
        list=$(echo "$list" | consolidate_list "$run")
        if [ "$plist" != "$list" ] ; then
            debug "------------------------------------------------------------------------------------"
            debug "$HEADER"
            debug "$list"
        else
            break
        fi
    done
    debug "------------------------------------------------------------------------------------"
    echo "$list"
}

repack_list() { # git.repo < list
    local repo=$1
    local start_date newpacks=0 pkeeps keeps=1 refs refdirs rtn
    local packedrefs=$(<"$repo/packed-refs")

    # so they don't appear touched after a noop refpacking
    if [ -z "$SW_REFS" ] ; then
        refs=$(cd "$repo/refs" ; find -depth)
        refdirs=$(cd "$repo/refs" ; find -type d -depth)
        debug "Before refs:"
        debug "$refs"
    fi

    # Find a private keep snapshot which has not changed from
    # before our start_date so private keep deletions during gc
    # can be detected
    while ! array_equals pkeeps "${keeps[@]}" ; do
       debug "Getting a private keep snapshot"
       private_keeps "$repo"
       keeps=("${pkeeps[@]}")
       debug "before keeps: ${keeps[*]}"
       start_date=$(date)
       private_keeps "$repo"
       debug "after keeps: ${pkeeps[*]}"
    done

    while read n has_keep size sha repack down up note; do
        if [ "$repack" = "y" ] ; then
            keep="$repo/objects/pack/pack-$sha.keep"
            info "Repacking $repo/objects/pack/pack-$sha.pack"
            [ -f "$keep" ] && rm -f "$keep"
        fi
    done

    ( cd "$repo" && git gc "${GC_OPTS[@]}" ) ; rtn=$?

    # Mark any files withoug a .keep with our .keep
    packs=("$repo"/objects/pack/pack-$SHA1.pack)
    for pack in "${packs[@]}" ; do
        if keep "$pack" ; then
            info "New pack: $pack"
            newpacks=$((newpacks+1))
        fi
    done

    # Record start_time.  If there is more than 1 new packfile, we
    # don't want to risk touching it with an older date since that
    # would prevent consolidation on the next run.  If the private
    # keeps have changed, then we should run next time no matter what.
    if [ $newpacks -le 1 ] || ! array_equals pkeeps "${keeps[@]}" ; then
        set_start_date "$repo" "$start_date" "$refs" "$refdirs" "$packedrefs" "${packs[@]}"
    fi

    return $rtn # we really only care about the gc error code
}

git_gc() { # git.repo
    local list=$(process_list "$1")
    if [ -z "$SW_V" ] ; then
        info "Running $PROG on $1.  git gc options: ${GC_OPTS[@]}"
        echo "$HEADER" >&2
        echo "$list" >&2 ;
    fi
    echo "$list" | repack_list "$1"
}


PROG=$(basename "$0")
HEADER="Id Keep Size           Sha1(or consolidation list)      Actions(repack down up note)"
KEEP=git-exproll
HEX='[0-9a-f]'
HEX10=$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX
SHA1=$HEX10$HEX10$HEX10$HEX10

RATIO=10
SW_N='' ; SW_V='' ; SW_T='' ; SW_REFS='' ; SW_LOOSE='' ; GC_OPTS=()
while [ $# -gt 0 ] ; do
    case "$1" in
        -u|-h)  usage ;;
        -n)  SW_N="$1" ;;
        -v)  SW_V="$1" ;;

        -t)  SW_T="$1" ;;
        --norefs)  SW_REFS="$1" ;;
        --noloose) SW_LOOSE="$1" ;;

        -r|--ratio)  shift ; RATIO="$1" ;;

        *)  [ $# -le 1 ] && break
            GC_OPTS=( "${GC_OPTS[@]}" "$1" )
            ;;
    esac
    shift
done


REPO="$1"
if ! is_repo "$REPO" ; then
    REPO=$REPO/.git
    is_repo "$REPO" || usage "($1) is not likely a git repo"
fi


if [ -z "$SW_N" ] ; then
    is_touched "$REPO" || { info "Repo untouched since last run" ; exit ; }
    git_gc "$REPO"
else
    is_touched "$REPO" || info "Repo untouched since last run, analyze anyway."
    process_list "$REPO" >&2
fi
