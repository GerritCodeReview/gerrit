#!/bin/bash

set -eu

readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
test_dir=$(dirname -- "$(readlink -f -- "$0")")
hook=$test_dir/tools/root/hooks/commit-msg

if [ -z "${TEST_TMPDIR-}" ] ; then
  TEST_TMPDIR=$(mktemp -d)
  trap cleanup EXIT
fi

function cleanup {
  rm -rf "$TEST_TMPDIR"
}

cd $TEST_TMPDIR

function fail {
  echo "FAIL: $1"
  exit 1
}

function prereq_modern_git {
  # "git interpret-trailers --where" was introduced in Git 2.15.0.
  git interpret-trailers -h 2>&1 | grep -e --where > /dev/null
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

function test_empty_with_cutoff {
  rm -f input
  cat << EOF > input
# Please enter the commit message for your changes.
# ------------------------ >8 ------------------------
# Do not modify or remove the line above.
# Everything below it will be ignored.
diff --git a/file.txt b/file.txt
index 625fd613d9..03aeba3b21 100755
--- a/file.txt
+++ b/file.txt
@@ -38,6 +38,7 @@
 context
 line
 
+hello, world
 
 context
 line
EOF
  if ${hook} input ; then
    fail "must fail on empty message"
  fi
}

function test_keep_cutoff_line {
  if ! prereq_modern_git ; then
    echo "old version of Git detected; skipping scissors test."
    return 0
  fi
  rm -f input
  cat << EOF > input
Do something nice

# Please enter the commit message for your changes.
# ------------------------ >8 ------------------------
# Do not modify or remove the line above.
# Everything below it will be ignored.
diff --git a/file.txt b/file.txt
index 625fd613d9..03aeba3b21 100755
--- a/file.txt
+++ b/file.txt
@@ -38,6 +38,7 @@
 context
 line
 
+hello, world
 
 context
 line
EOF
  ${hook} input || fail "failed hook execution"
  grep '>8' input || fail "lost cut-off line"
  sed -n -e '1,/>8/ p' input >top
  grep '^Change-Id' top || fail "missing Change-Id above cut-off line"
}

# a Change-Id already set is preserved.
function test_preserve_changeid {
  cat << EOF > input
bla bla

Change-Id: I123
EOF

  ${hook} input || fail "failed hook execution"

  found=$(grep -c '^Change-Id' input) || :
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
  found=$(grep -c '^Change-Id: I123' input) || :
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Id: I123, want 1"
  fi
}

# Change-Id should not be inserted if gerrit.createChangeId=false
function test_suppress_changeid {
  cat << EOF > input
bla bla
EOF

  git config gerrit.createChangeId false
  ${hook} input || fail "failed hook execution"
  git config --unset gerrit.createChangeId
  found=$(grep -c '^Change-Id' input) || :
  if [[ "${found}" != "0" ]]; then
    fail "got ${found} Change-Ids, want 0"
  fi
}

function suppress_squash_like {
  cat << EOF > input
$1! bla bla
EOF

  ${hook} input || fail "failed hook execution"
  found=$(grep -c '^Change-Id' input || true)
  if [[ "${found}" != "0" ]]; then
    fail "got ${found} Change-Ids, want 0"
  fi
}

function test_suppress_squash {
  # test for standard git prefixes
  suppress_squash_like squash
  suppress_squash_like fixup
  suppress_squash_like amend
  # test for custom prefixes
  suppress_squash_like temp
  suppress_squash_like nopush
}

function test_always_create {
  cat << EOF > input
squash! bla bla
EOF

  git config gerrit.createChangeId always
  ${hook} input || fail "failed hook execution"
  git config --unset gerrit.createChangeId
  found=$(grep -c '^Change-Id' input || true)
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
}

# gerrit.reviewUrl causes us to create Link instead of Change-Id.
function test_link {
  cat << EOF > input
bla bla
EOF

  git config gerrit.reviewUrl https://myhost/
  ${hook} input || fail "failed hook execution"
  git config --unset gerrit.reviewUrl
  found=$(grep -c '^Change-Id' input) || :
  if [[ "${found}" != "0" ]]; then
    fail "got ${found} Change-Ids, want 0"
  fi
  found=$(grep -c '^Link: https://myhost/id/I' input) || :
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Link footers, want 1"
  fi
}

function test_preserve_link {
  cat << EOF > input
bla bla

Link: https://myhost/id/I1234567890123456789012345678901234567890
EOF

  git config gerrit.reviewUrl https://myhost/
  ${hook} input || fail "failed hook execution"
  git config --unset gerrit.reviewUrl
  found=$(grep -c '^Change-Id' input) || :
  if [[ "${found}" != "0" ]]; then
    fail "got ${found} Change-Ids, want 0"
  fi
  found=$(grep -c '^Link: https://myhost/id/I' input) || :
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Link footers, want 1"
  fi
  found=$(grep -c '^Link: https://myhost/id/I1234567890123456789012345678901234567890$' input) || :
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Link: https://myhost/id/I123..., want 1"
  fi
}

# Change-Id goes after existing trailers.
function test_at_end {
  cat << EOF > input
bla bla

Bug: #123
EOF

  ${hook} input || fail "failed hook execution"
  result=$(tail -1 input | grep ^Change-Id) || :
  if [[ -z "${result}" ]] ; then
    echo "after: "
    cat input

    fail "did not find Change-Id at end"
  fi
}

# Change-Id goes after --- line.
function test_triple_dash {
  cat << EOF > input
bla bla

---

bla bla
EOF

  ${hook} input || fail "failed hook execution"
  result=$(tail -1 input | grep ^Change-Id) || :
  if [[ -z "${result}" ]] ; then
    echo "after: "
    cat input

    fail "did not find Change-Id at end"
  fi
}

# Change-Id goes before Signed-off-by trailers.
function test_before_signed_off_by {
  cat << EOF > input
bla bla

Bug: #123
Signed-off-by: Joe User
EOF

  ${hook} input || fail "failed hook execution"
  result=$(tail -2 input | head -1 | grep ^Change-Id) || :
  if [[ -z "${result}" ]] ; then
    echo "after: "
    cat input

    fail "did not find Change-Id before Signed-off-by"
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

  result=$(tail -1 input | grep ^Change-Id) || :
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

  found=$(grep -c '^Change-Id' input) || :
  if [[ "${found}" != "1" ]]; then
    fail "got ${found} Change-Ids, want 1"
  fi
  found=$(grep -c '^Change-Id: I123' input) || :
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
