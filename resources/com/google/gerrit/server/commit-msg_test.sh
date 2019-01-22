#!/bin/bash

set -eu

hook=$(pwd)/resources/com/google/gerrit/server/tools/root/hooks/commit-msg

cd $TEST_TMPDIR

function fail {
  echo "FAIL: $1"
  exit 1
}

function test_nonexistent_argument {
  rm -f input
  if ${hook} input ; then
    fail "must fail for non-existent input"
  fi
}

function test_empty {
  rm -f input
  touch input
  if ${hook} input ; then
    fail "must fail on empty message"
  fi
}

function test_empty_with_comments {
  rm -f input
  cat << EOF > input
# comment

# comment2
EOF
  if ${hook} input ; then
    fail "must fail on empty message"
  fi
}

function test_empty_with_verbose_diff {
  rm -f input
  cat << EOF > input

# Please enter the commit message for your changes. Lines starting
# with '#' will be ignored, and an empty message aborts the commit.
#
# On branch master
# Your branch is up to date with 'origin/master'.
#
# Changes to be committed:
#	modified:   commit-msg
#
# ------------------------ >8 ------------------------
# Do not modify or remove the line above.
# Everything below it will be ignored.
diff --git a/resources/com/google/gerrit/server/tools/root/hooks/commit-msg b/resources/com/google/gerrit/server/tools/root/hooks/commit-msg
index 53f29950e1..ad7897a3b6 100755
--- a/resources/com/google/gerrit/server/tools/root/hooks/commit-msg
+++ b/resources/com/google/gerrit/server/tools/root/hooks/commit-msg
@@ -33,6 +33,17 @@ dest="$1.tmp.${random}"

 trap 'rm -f "${dest}"' EXIT


EOF
  if ${hook} input ; then
    fail "must fail on empty message"
  fi
}

function test_preserve_changeid_with_verbose_diff {
rm -f input
cat << EOF > input
bla bla

Change-Id: I123
# Please enter the commit message for your changes. Lines starting
# with '#' will be ignored, and an empty message aborts the commit.
#
# On branch master
# Your branch is up to date with 'origin/master'.
#
# Changes to be committed:
#	modified:   commit-msg
#
# ------------------------ >8 ------------------------
# Do not modify or remove the line above.
# Everything below it will be ignored.
diff --git a/resources/com/google/gerrit/server/tools/root/hooks/commit-msg b/resources/com/google/gerrit/server/tools/root/hooks/commit-msg
index 53f29950e1..ad7897a3b6 100755
--- a/resources/com/google/gerrit/server/tools/root/hooks/commit-msg
+++ b/resources/com/google/gerrit/server/tools/root/hooks/commit-msg
@@ -33,6 +33,17 @@ dest="$1.tmp.${random}"

 trap 'rm -f "${dest}"' EXIT

EOF
  ${hook} input || fail "failed hook execution"

  found=$(grep -c '^Change-Id' input)
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
  found=$(grep -c '^Change-Id: I123' input)
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Id: I123, want 1"
  fi
}

# a Change-Id already set is preserved.
function test_preserve_changeid {
  cat << EOF > input
bla bla

Change-Id: I123
EOF

  ${hook} input || fail "failed hook execution"

  found=$(grep -c '^Change-Id' input)
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
  found=$(grep -c '^Change-Id: I123' input)
  if [[ "${found}" != "1" ]]; then
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

function test_dash_at_end {
  if [[ ! -x /bin/dash ]] ; then
    echo "/bin/dash not installed; skipping dash test."
    return
  fi

  cat << EOF > input
bla bla

Bug: #123
EOF

  /bin/dash ${hook} input || fail "failed hook execution"

  result=$(tail -1 input | grep ^Change-Id)
  if [[ -z "${result}" ]] ; then
    echo "after: "
    cat input

    fail "did not find Change-Id at end"
  fi
}

function test_preserve_dash_changeid {
  if [[ ! -x /bin/dash ]] ; then
    echo "/bin/dash not installed; skipping dash test."
    return
  fi

  cat << EOF > input
bla bla

Change-Id: I123
EOF

  /bin/dash ${hook} input || fail "failed hook execution"

  found=$(grep -c '^Change-Id' input)
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
  found=$(grep -c '^Change-Id: I123' input)
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Id: I123, want 1"
  fi
}


# Test driver.
git init
for func in $( declare -F | awk '{print $3;}' | sort); do
  case ${func} in
    test_*)
      echo "=== testing $func"
      ${func}
      echo "--- done    $func"
      ;;
  esac
done
