#!/bin/

genhtml=$(which genhtml)
if [[ -z "$genhtml" ]]; then
    echo "Install 'genhtml' (contained in the 'lcov' package)"
    exit 1
fi

destdir="$1"
if [[ -z "$destdir" ]]; then
    destdir=$(mktemp -d /tmp/gerritcov.XXXXXX)
fi


echo "Running 'bazel coverage'; this may take a while"

# coverage is expensive to run; use --jobs=2 to avoid overloading the
# machine.
bazel coverage -k --jobs=2 -- ... -//gerrit-common:auto_value_tests


# The coverage data contains filenames relative to the Java root, and
# genhtml has no logic to search these elsewhere. Workaround this
# limitation by running genhtml in a directory with the files in the
# right place. Also -inexplicably- genhtml wants to have the source
# files relative to the output directory.
mkdir -p ${destdir}/coverage-report
cp -a */src/{main,test}/java/* ${destdir}/coverage-report

base=$( bazel info  bazel-testlogs)
for f in $(find ${base}  -name 'coverage.dat') ; do
  cp $f ${destdir}/$(echo $f| sed "s|$base/||" | sed "s|/|_|g")
done

cd ${destdir}
rm -f $(find -name '*coverage.dat' -size 0)

genhtml -o coverage-report --ignore-errors source *coverage.dat

echo "coverage report at file:///${destdir}/index.html"
