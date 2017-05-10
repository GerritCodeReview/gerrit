#!/bin/

genhtml=$(which genhtml)
if [[ -z "$genhtml" ]]; then
    echo "Install 'genhtml' (contained in the 'lcov' package)"
    exit 1
fi

echo "Running 'bazel coverage'; this may take a while"

# coverage is expensive to run; use --jobs=2 to avoid overloading the
# machine.
bazel coverage -k --jobs=2 -- ... -//gerrit-common:auto_value_tests

tmp=$(mktemp -d /tmp/gerritcov.XXXXXX)
dest=$(pwd)


# The coverage data contains filenames relative to the Java root, and
# genhtml has no logic to search these elsewhere. Workaround this
# limitation by running genhtml in a directory with the files in the
# right place. Also -inexplicably- genhtml wants to have the source
# files relative to the output directory.
mkdir -p ${tmp}/coverage-report
cp -a */src/{main,test}/java/* ${tmp}/coverage-report

base=$( bazel info  bazel-testlogs)
for f in $(find ${base}  -name 'coverage.dat') ; do
  cp $f ${tmp}/$(echo $f| sed "s|$base/||" | sed "s|/|_|g")
done

cd ${tmp}
rm -f $(find -name '*coverage.dat' -size 0)

genhtml -o coverage-report --ignore-errors source *coverage.dat

tar -cjf ${dest}/coverage-report.tar.bz2 coverage-report/
