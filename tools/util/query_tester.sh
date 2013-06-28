#!/bin/bash

usage() {
    cat <<EOF
  query_suite run -h <host> -i <suite_dir> -o <output_dir> [-s <suite>]

  Run query test suites

  A query test suite must be a subdirectory of suite_dir and contain
  either files with queries in them, or if the suite name looks like
  "values_operator", the files must contain values for the operator
  instead of full queries.

  The query outputs for each suite will be stored in files named
  after their suites and input file names under the output_dir.

  Special files:

  append_all    Append this text to all queries in the test suite
  prepend_all   Prepend this text to all queries in the test suite

EOF
    exit
}

e() { [ -n "$VERBOSE" ] && echo "$@" >&2 ; "$@" ; }

query()  { # host query
    local host=$1 query="$2"
    e ssh -p 29418 "$host" gerrit query --format json "$query"
}

run_suite() { # host idir odir suite operator
    local host=$1 idir=$2 odir=$3 suite=$4 operator=$5
    local qfile files pre='' post='' fdir=$idir

    mkdir -p "$odir/$suite"

    echo "$suite" | grep -q "^values_" && operator=$(echo "$suite" | sed '-es/^values_//')

    if [ -z "$suite" ] ; then
        files=($(cd "$idir" ; echo *))
    else
        fdir=$idir/$suite
        files=($(cd "$idir" ; echo $suite/*))
    fi

    [ -f "$fdir/prepend_all" ] && pre=$(< "$fdir/prepend_all")
    [ -f "$fdir/append_all" ] && post=$(< "$fdir/append_all")

    for qfile in "${files[@]}" ; do
        [ "$(basename "$qfile")" = "prepend_all" ] && continue
        [ "$(basename "$qfile")" = "append_all" ] && continue
        if [ -d "$idir/$qfile" ] ; then
            run_suite "$host" "$idir" "$odir" "$qfile" "$operator"
        else
            if [ -n "$operator" ] ; then
                query "$host" "$pre $operator:{$(< "$idir/$qfile")} $post" > "$odir/$qfile"
            else
                query "$host" "$pre $(< "$idir/$qfile") $post" > "$odir/$qfile"
            fi
        fi
    done
}

cmd=''
while [ $# -gt 0 ] ; do
    case "$1" in
        -v) VERBOSE=$1 ;;

        -h) shift ; HOST=$1 ;;
        -s) shift ; SUITE=$1 ;;
        -i) shift ; IDIR=$1 ;;
        -o) shift ; ODIR=$1 ;;

        run) cmd=$1 ;;

        --exec) shift ; "$@" ; exit ;;

        *) usage ;;
    esac
    shift
done

case "$cmd" in
    run)
        [ -z "$HOST" ] && usage
        [ -z "$IDIR" ] && usage
        [ -z "$ODIR" ] && usage
        [ -z "$SUITE" ] && SUITE=""
        run_suite "$HOST" "$IDIR" "$ODIR" "$SUITE" ; exit
    ;;
esac
