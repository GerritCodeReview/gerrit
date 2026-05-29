#!/usr/bin/env bash
set -euo pipefail

ALLOWLIST="${1:?missing allowlist file}"
GENERATED="${2:?missing generated manifest file}"

if [[ ! -f "${ALLOWLIST}" ]]; then
  echo ""
  echo "FAIL: Missing WAR allowlist:"
  echo "  ${ALLOWLIST}"
  echo ""
  echo "To generate it from the current WAR packaging inputs:"
  echo ""
  echo "  bazelisk build //:release.war.jars.txt"
  echo "  cp bazel-bin/release.war.jars.txt ${ALLOWLIST}"
  echo ""
  exit 1
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

# Strip comments/blank lines from allowlist; normalize both sides.
grep -vE '^\s*(#|$)' "${ALLOWLIST}" | sort -u > "${tmpdir}/allowlist.norm"
grep -vE '^\s*$' "${GENERATED}" | sort -u > "${tmpdir}/generated.norm"

if ! diff -u "${tmpdir}/allowlist.norm" "${tmpdir}/generated.norm"; then
  echo ""
  echo "FAIL: WAR packaged third-party JAR set changed."
  echo ""
  echo "This means the set of third-party runtime dependencies that would be"
  echo "packaged into release.war no longer matches the checked-in allowlist."
  echo ""
  echo "If this change is expected and reviewed, refresh the allowlist with:"
  echo ""
  echo "  bazelisk build //:release.war.jars.txt"
  echo "  cp bazel-bin/release.war.jars.txt ${ALLOWLIST}"
  echo ""
  echo "Then re-run:"
  echo ""
  echo "  bazelisk test //Documentation:check_release_war_jars"
  echo ""
  exit 1
fi
