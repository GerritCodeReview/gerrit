#!/bin/bash

set -u

# TODO(davido): Figure out what to do if running alone and not invoked from bazel.

# $1 may be either $(JAVA) or $(JAVABASE)
JAVA="$1"

if [[ -n "${TEST_SRCDIR:-}" ]]; then
  case "$JAVA" in
    external/*)
      JAVA="${TEST_SRCDIR}/${JAVA#external/}"
      ;;
    /*)
      if [[ "$JAVA" == "${TEST_SRCDIR}/_main/external/"* ]]; then
        JAVA="${TEST_SRCDIR}/${JAVA#"${TEST_SRCDIR}/_main/external/"}"
      fi
      ;;
    *)
      [[ "$JAVA" == */* ]] && JAVA="$PWD/$JAVA"
      ;;
  esac
else
  [[ "$JAVA" =~ ^(/|[^/]+$) ]] || JAVA="$PWD/$JAVA"
fi

# If the resolved path is a Java home directory, use its bin/java
if [[ -d "$JAVA" ]]; then
  JAVA="$JAVA/bin/java"
fi

TESTS="t1 t2 t3"

LF=$'\n'
PASS=""
FAIL=""

if [[ -z "${TEST_SRCDIR:-}" ]]; then
  # Assume running standalone.
  GERRIT_WAR="../../bazel-bin/gerrit.war"
  SRCDIR="."
else
  # Assume running from bazel.
  GERRIT_WAR="$(pwd)/gerrit.war"
  SRCDIR="prologtests/examples"
fi

# Default GERRIT_TMP is ~/.gerritcodereview/tmp,
# which won't be writable in a bazel test sandbox.
/bin/mkdir -p /tmp/gerrit
export GERRIT_TMP=/tmp/gerrit

for T in $TESTS; do
  pushd "$SRCDIR" >/dev/null

  echo "### Running test ${T}.pl"
  echo "[$T]." | "$JAVA" -jar "$GERRIT_WAR" prolog-shell -q -s load.pl
  RC=$?

  if [[ "$RC" != "0" ]]; then
    echo "### Test ${T}.pl failed."
    FAIL="${FAIL}${LF}FAIL: Test ${T}.pl"
  else
    PASS="${PASS}${LF}PASS: Test ${T}.pl"
  fi

  popd >/dev/null
done

echo "$PASS"

if [[ -n "$FAIL" ]]; then
  echo "$FAIL"
  exit 1
fi
