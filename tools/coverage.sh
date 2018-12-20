#!/bin/bash
#
# Usage
#
#   COVERAGE_CPUS=32 tools/coverage.sh [/path/to/report-directory/]
#
# COVERAGE_CPUS defaults to 2, and the default destination is a temp
# dir.

genhtml=$(which genhtml)
if [[ -z "${genhtml}" ]]; then
    echo "Install 'genhtml' (contained in the 'lcov' package)"
    exit 1
fi

destdir="$1"
if [[ -z "${destdir}" ]]; then
    destdir=$(mktemp -d /tmp/gerritcov.XXXXXX)
fi

echo "Running 'bazel coverage'; this may take a while"

# coverage is expensive to run; use --jobs=2 to avoid overloading the
# machine.
bazel coverage -k --jobs=${COVERAGE_CPUS:-2} -- ... -//javatests/com/google/gerrit/common:auto_value_tests

# The coverage data contains filenames relative to the Java root, and
# genhtml has no logic to search these elsewhere. Workaround this
# limitation by running genhtml in a directory with the files in the
# right place. Also -inexplicably- genhtml wants to have the source
# files relative to the output directory.
mkdir -p ${destdir}/
cp -a */src/{main,test}/java/* ${destdir}/

base=$(bazel info bazel-testlogs)
for f in $(find ${base}  -name 'coverage.dat') ; do
  cp $f ${destdir}/$(echo $f| sed "s|${base}/||" | sed "s|/|_|g")
done

cd ${destdir}
find -name '*coverage.dat' -size 0 -delete

genhtml -o . --ignore-errors source *coverage.dat

echo "coverage report at file://${destdir}/index.html"
