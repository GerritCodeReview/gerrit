#!/bin/bash
#
# Todo:
# We could projects which are dormant for a certain period of time
#  to rollup all of their packfiles into 1.  To prevent all of a whole
#  list of repos from rolling up on the same day, it would be good to
#  add a random amount of runs to these rollups.
# We need to verify that pruning is correct
# Add some test stats
usage() { # error_message

    cat <<-EOF
    usage: $(basename $0) [-unvt] [-r|--ratio number] [git gc option...] git.repo

    -u usage
    -v verbose
    -n dry-run           don't actually repack anything
    -t touch             treat repo as if it had been touched
    -r ratio <number>    packfile ratio to aim for

     git.repo            to run gc against

    Garbage collect using a pseudo logarithmic packfile maintenance
    approach.  This approach attempts to mimnimize packfile churn
    by keeping several generations of packfiles around of varying
    sizes and only consolidating packfiles (or loose objects) which
    are either new packfiles, or packfiles close to the same size as
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

    Id:    numbers preceeded by a c are estimated "c pack" files
    Keep:  - none, k privat keep, o our keep
    Size:  in disk blocks (default du output)
    Sha1:  of pack file, or consolidation list of packfile ids
    Actions
     repack: - no, y yes
     down:   - noop, ^ consolidate with a file above
     up:     - noop, v consolidate with a file below
     note:   Human description of script decisions
             New (file is a new packfile)
             Consolidate with:<list of packfile ids>
             (too far from:<list of packfile ids>)

    On the first pass, always consolidate any new pack files
    (w/ loose objects) along with any packfiles which are
    within the ratio size of their predecessors (note, the list
    is ordered by increasing size).  After each consolidation,
    insert a fack consolidaton, or "c pack", to naively
    represent the size and ordered positioning of the anticipated
    new consolidated pack.  Every time a new pack is planned,
    rescan the list in case the new "c pack" would cause more
    consolidation...

    Once the packfiles which need consolidation is determined, the
    packfiles which will not be consolidated are marked with a .keep
    file, and those which will be consolidated will have their .keep
    removed if they have one.  Thus, the packfiles with a .keep will
    will not get repacked.

    Packfile consolidation is determined by the --ratio parameter
    (default is 10).  This ratio is somewhat of a tradeoff.  The
    smaller the number, the more packfiles will be kept on average.
    This increases disk utilization somewhat.  However, a larger
    ratio causes greater churn and may increase disk utilization due
    to deleted packfiles not being reclaimed since they may still be
    kept open by long running applications such as Gerrit).  Sane
    ratio values are probably between 2 and 10.
EOF

    [ -n "$1" ] && info "ERROR $1"

    exit
}

packs_sizes() { # git.repo
    du -s "$1"/objects/pack/pack-$SHA1.pack | sort -n 2> /dev/null
}

is_ourkeep() { grep -q "$KEEP" "$1" 2> /dev/null ; } # keep
has_ourkeep() { is_ourkeep "$(keep_for "$1")" ; } # pack
has_keep() { [ -f "$(keep_for "$1")" ] ; } # pack
is_repo() { [ -d "$1/objects"  -a  -d "$1/refs" ] ; } # git.repo

keep() { # pack   # returns true if we added our keep
    keep=$(keep_for "$1")
    [ -f "$keep" ] && return 1
    echo "$KEEP" > "$keep"
    return 0
}

keep_for() { # packfile
    local keep=$(echo "$1"| sed -es'/\.pack$/.keep/')
    [ "${keep/.keep}" = "$keep" ] && return 1
    echo "$keep"
}

is_tooclose() { [ "$(($1 * $RATIO))" -gt "$2" ] ; } # smaller larger

is_touched() { # git.repo
    local repo=$1 keep ours newer
    [ -n "$SW_T" ] && return 0

    for keep in "$repo"/objects/pack/pack-$SHA1.keep ; do
        is_ourkeep "$keep" && { ours=$keep ; break ; }
    done
    [ -z "$ours" ] && return 0

    newer=$(find "$repo" \
                  -type f -cnewer "$ours" '\!' -wholename "$repo/info/refs" \
                  -print -quit 2>/dev/null)
    [ -z "$newer" ] && return 1

    deb "Newer:"
    deb "$newer"
    return 0
}

deb() { [ -n "$SW_V" ] && info "$1" ; }
info() { echo "$1" >&2 ; }

sha_for() { echo "$1"| sed -es'|\(.*/\)*pack-\([^.]*\)\..*$|\2|' ; } # pack_or_keep_file

sort_list() { # < list > formatted_list
    # n has_keep size sha repack down up note
    awk '{ note=$8; for(i=8;i<NF;i++) note=note " "$(i+1)
           printf("%-5s %s %-14s %-40s %s %s %s %s\n", \
                     $1,$2,   $3,  $4, $5,$6,$7,note)}' |\
        sort -k 3,3n
}

last_entry() { # isRepack pline repackline
    local size_hit=$1 pline=$2 repackline=$3

    if [ -n "$pline" ] ; then
        if [ -n "$size_hit" ] ; then
            echo "$repack_line"
        else
            echo "$pline"
        fi
    fi
}

unique() { # [args...] > unique_words
    local lines=$(while [ $# -gt 0 ] ; do echo "$1" ; shift ; done)
    lines=$(echo "$lines"| sort -u)
    echo $lines  # as words
}

outfs() { # fs [args...] > argfs...
    local fs=$1 ; shift
    [ $# -gt 0 ] && echo -n "$1" ; shift
    while [ $# -gt 0 ] ; do echo -n "$fs$1" ; shift ; done
}

note_consolidate() { # note entry > note (no duplicated consolidated entries)
    local note=$1 entry=$2 entries=() ifs=$IFS
    if  echo "$note"| grep -q 'Consolidate with:[0-9,c]' ; then
        IFS=,
        entries=( $(echo "$note"| sed -es'/^.*Consolidate with:\([0-9,c]*\).*$/\1/') )
        note=( $(echo "$note"| sed -es'/Consolidate with:[0-9,c]*//') )
        IFS=$ifs
    fi
    entries=( $(unique "${entries[@]}" "$entry") )
    echo "$note Consolidate with:$(outfs , "${entries[@]}")"
}

note_toofar() { # note entry > note (no duplicated "too far" entries)
    local note=$1 entry=$2 entries=() ifs=$IFS
    if  echo "$note"| grep -q '(too far from:[0-9,c]*)' ; then
        IFS=,
        entries=( $(echo "$note"| sed -es'/^.*(too far from:\([0-9,c]*\)).*$/\1/') )
        note=( $(echo "$note"| sed -es'/(too far from:[0-9,c]*)//') )
        IFS=$ifs
    fi
    entries=( $(unique "${entries[@]}" "$entry") )
    echo "$note (too far from:$(outfs , "${entries[@]}"))"
}

init_list() { # git.repo > shortlist
    local repo=$1 file
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
    local run=$1  sum=0 psize=0 sum_size=0 size_hit pn clist pline repackline
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

            # By preventing conslidated file from being marked "repack"
            # they won't get keeps
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
        list=$(echo "$list"| consolidate_list "$run")
        if [ "$plist" != "$list" ] ; then
            deb "------------------------------------------------------------------------------------"
            deb "$HEADER"
            deb "$list"
        else
            break
        fi
    done
    deb "------------------------------------------------------------------------------------"
    echo "$list"
}

repack_list() { # git.repo < list
    local repo=$1 start_date=$(date) minsize newpacks=0 packs

    while read n has_keep size sha repack down up note; do
        if [ "$repack" = "y" ] ; then
            keep="$repo/objects/pack/pack-$sha.keep"
            info "Repacking $keep"
            [ -f "$keep" ] && rm -f "$keep"
        fi
    done

    ( cd "$repo" && git gc "${GC_OPTS[@]}" )

    # Mark any files withoug a .keep with our .keep
    packs=("$repo"/objects/pack/pack-$SHA1.pack)
    for pack in "${packs[@]}" ; do
        if keep "$pack" ; then
            info "New pack: $pack"
            newpacks=$((newpacks+1))
        fi
    done

    # record start_time.  If there were more than 1 new pack file, we
    # don't want to risk touching it with an older date since that
    # would prevent consolidation on the next run.  If there are no
    # new packfiles, then we haven't chagnes anything, so don't touch
    # anything or it will force it to run again next time.
    if [ $newpacks -eq 1 ] ; then
        for pack in "${packs[@]}" ; do
            touch -c -d "$start_date" "$pack" "$(keep_for "$pack")"
            deb "Setting start date on: $pack $(keep_for "$pack")"
        done
    fi
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
RATIO=10
GC_OPTS=()
HEX='[0-9a-f]'
HEX10=$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX$HEX
SHA1=$HEX10$HEX10$HEX10$HEX10

while [ $# -gt 0 ] ; do
    case "$1" in
        -u)  usage ;;
        -n)  SW_N="$1" ;;
        -t)  SW_T="$1" ;;
        -v)  SW_V="$1" ;;
        -r|--ratio)  shift ; RATIO="$1" ;;
        --exec) shift ; "$@" ; exit ;;
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
