#!/bin/bash

set -eu

hook=$(pwd)/resources/com/google/gerrit/server/tools/root/hooks/commit-msg

cd $TEST_TMPDIR

function fail {
  echo "$1"
  exit 1
}

function test_executable {
  cat << EOF > input
ABC
EOF
  ${hook} input || fail "must be executable"
}

function test_empty {
  rm -f input
  touch input
  { ${hook} input && fail "must fail on empty message" ; } || true
}

# a Change-Id already set is preserved.
function test_preserve_changeid {
  cat << EOF > input
bla bla

Change-Id: I123
EOF

  ${hook} input || fail "failed hook execution"

  found=$(grep '^Change-Id' input | wc -l)
  if [[ "$found" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
  found=$(grep '^Change-Id: I123' input | wc -l)
  if [[ "$found" != "1" ]]; then
    fail "got ${found} Change-Id: I123, want 1"
  fi
}

# Change-Id goes after existing trailers.
function test_at_end {
  cat << EOF > input
bla bla

Bug: #123
EOF

  ${hook} input || fail "failed hook execution"
  result=$(tail -1 input | grep ^Change-Id)
  if [[ -z "${result}" ]] ; then
    echo "after: "
    cat input

    fail "did not find Change-Id at end"
  fi
}

# Test driver.

for func in $( declare -F | awk '{print $3;}' | sort); do
  case $func in
    test_*)
      echo "=== testing $func"
      $func
      echo "--- done    $func"
      ;;
  esac
done
