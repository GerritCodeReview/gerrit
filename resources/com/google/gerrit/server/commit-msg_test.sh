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

function test_preserve_changeid_with_non_colon_tags {
  cat << EOF > input
bla bla

TAG=agy
CONV=123
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

# Creates a repository in $1 whose HEAD is a commit carrying a Jujutsu style
# change-id header, with the hook installed so that git invokes it for real.
function setup_jj_repo {
  rm -rf "$1"
  mkdir -p "$1"
  git init -q "$1"
  cp ${hook} "$1/.git/hooks/commit-msg"
  chmod +x "$1/.git/hooks/commit-msg"
  (
    cd "$1"
    git config user.name "Joe User"
    git config user.email juser@example.com
    echo base > base.txt
    git add base.txt
    git commit -q -m base --no-verify

    echo jj > jj.txt
    git add jj.txt
    ident="Joe User <juser@example.com> 1700000000 +0000"
    {
      echo "tree $(git write-tree)"
      echo "parent $(git rev-parse HEAD)"
      echo "author ${ident}"
      echo "committer ${ident}"
      echo "change-id qpvuntsmwlqtpsluzzsnyyzlmlwvmxvq"
      echo
      echo "Commit written by Jujutsu"
    } | git hash-object -t commit -w --stdin > jj_commit
    git reset -q --hard "$(cat jj_commit)"
  )
}

# Amending a commit that carries a change-id header keeps the header, so no
# footer is added.
function test_suppress_changeid_when_amending_header_commit {
  setup_jj_repo jj-amend
  (
    cd jj-amend
    echo more >> jj.txt
    git add jj.txt
    git commit -q --amend -m "Amended by git" || fail "failed to amend"

    found=$(git log -1 --format=%B | grep -c '^Change-Id') || :
    if [[ "${found}" != "0" ]]; then
      fail "got ${found} Change-Ids on amended jj commit, want 0"
    fi
    found=$(git cat-file commit HEAD | sed -e '/^$/q' | grep -c '^change-id ') || :
    if [[ "${found}" != "1" ]]; then
      fail "got ${found} change-id headers after amend, want 1"
    fi
  )
}

# A new commit on top of a jj commit has no header of its own, so it must still
# get a footer even though HEAD carries one.
function test_create_changeid_for_commit_on_top_of_header_commit {
  setup_jj_repo jj-child
  (
    cd jj-child
    echo child > child.txt
    git add child.txt
    git commit -q -m "Plain git commit" || fail "failed to commit"

    found=$(git log -1 --format=%B | grep -c '^Change-Id') || :
    if [[ "${found}" != "1" ]]; then
      fail "got ${found} Change-Ids on child of jj commit, want 1"
    fi
  )
}

# gerrit.createChangeId=always overrides the header suppression.
function test_always_create_overrides_header_suppression {
  setup_jj_repo jj-always
  (
    cd jj-always
    git config gerrit.createChangeId always
    echo more >> jj.txt
    git add jj.txt
    git commit -q --amend -m "Amended by git" || fail "failed to amend"

    found=$(git log -1 --format=%B | grep -c '^Change-Id') || :
    if [[ "${found}" != "1" ]]; then
      fail "got ${found} Change-Ids with createChangeId=always, want 1"
    fi
  )
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
