#!/bin/bash

# TODO(davido): Figure out what to do if running alone and not invoked from bazel
case $1 in
  "/"*) JAVABASE="$1" ;;
  *) JAVABASE="${PWD}/$1" ;;
esac
shift

TESTS="t1 t2 t3"

# Note that both t1.pl and t2.pl test code in rules.pl.
# Unit tests are usually longer than the tested code.
# So it is common to test one source file with multiple
# unit test files.

LF=$'\n'
PASS=""
FAIL=""

echo "#### TEST_SRCDIR = ${TEST_SRCDIR}"

if [ "${TEST_SRCDIR}" == "" ]; then
  # Assume running alone
  GERRIT_WAR="../../bazel-bin/gerrit.war"
  SRCDIR="."
else
  # Assume running from bazel
  GERRIT_WAR=`pwd`/gerrit.war
  SRCDIR="prologtests/examples"
fi

# Default GERRIT_TMP is ~/.gerritcodereview/tmp,
# which won't be writable in a bazel test sandbox.
/bin/mkdir -p /tmp/gerrit
export GERRIT_TMP=/tmp/gerrit

for T in $TESTS
do

  pushd $SRCDIR

  # Unit tests do not need to define clauses in packages.
  # Use one prolog-shell per unit test, to avoid name collision.
  echo "### Running test ${T}.pl"
  echo "[$T]." | "${JAVABASE}/bin/java" -jar ${GERRIT_WAR} prolog-shell -q -s load.pl

  if [ "x$?" != "x0" ]; then
    echo "### Test ${T}.pl failed."
    FAIL="${FAIL}${LF}FAIL: Test ${T}.pl"
  else
    PASS="${PASS}${LF}PASS: Test ${T}.pl"
  fi

  popd

  # java -jar ../../bazel-bin/gerrit.war prolog-shell -s $T < /dev/null
  # Calling prolog-shell with -s flag works for small files,
  # but got run-time exception with t3.pl.
  #   com.googlecode.prolog_cafe.exceptions.ReductionLimitException:
  #   exceeded reduction limit of 1048576
done

echo "$PASS"

if [ "$FAIL" != "" ]; then
  echo "$FAIL"
  exit 1
fi
