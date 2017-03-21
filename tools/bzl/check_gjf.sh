#!/bin/sh

set -eux

found_diff="0"

tmp=$(mktemp -d /tmp/gjf.XXXXXX)
mkdir ${tmp}/{before,after}

ws=$1
shift
pkg=$1
shift

prefix=${TEST_SRCDIR}/${ws}

JAR=${prefix}/$1
shift
JAVA=${prefix}/$1
shift

for f in "$@"; do
    src=${pkg}/${f}
    dst=${prefix}/${src}
    d=$(dirname ${dst})
    mkdir -p ${tmp}/{before,after}/${d}
    for where in before after; do
        cp $src ${tmp}/${where}/${dst}
    done
done

# TODO(hanwen): rather than diffing, use the --validate flag when it
# is available.
(cd ${tmp}/after; find -name  '*.java' | xargs ${JAVA} -jar ${JAR} -i)

cd ${tmp}

# this must be the last line.
diff -urN before after
