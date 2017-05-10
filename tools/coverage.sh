#!/bin/

genhtml=$(which genhtml)
if [[ -z "$genhtml" ]]; then
    echo "Install 'genhtml' (contained in the 'lcov' package"
    exit 1
fi

echo "Running 'bazel coverage'; this may take a while"
# coverage is expensive to run; use --jobs=2 to avoid overloading the
# machine.
bazel coverage -k --jobs=2 ...

tmp=$(mktemp -d /tmp/gerritcov.XXXXXX)
dest=$(pwd)
cp -a */src/{main,test}/java/* ${tmp}

base=$( bazel info  bazel-testlogs)
for f in $(find ${base}  -name 'coverage.dat') ; do
  cp $f ${tmp}/$(echo $f| sed "s|$base/||" | sed "s|/|_|g")
done

cd ${tmp}
rm -f $(find -name '*coverage.dat' -size 0)
genhtml -o . --ignore-errors source *coverage.dat

find -name '*.java' -or -name '*coverage.dat' -type f -exec rm '{}' ';'
mkdir gerrit-coverage
mv * gerrit-coverage/
tar -cjf ${dest}/coverage.tar.bz2 gerrit-coverage/
