#!/bin/bash
set -euo pipefail

report="$(find "${TEST_SRCDIR}" -path '*/ci_boundary_probe*/report.json' -type f -print -quit)"
if [[ -z "${report}" ]]; then
  echo "CI_BOUNDARY_REPORT_NOT_FOUND" >&2
  exit 1
fi

echo "CI_BOUNDARY_REPORT_BEGIN"
cat "${report}"
echo "CI_BOUNDARY_REPORT_END"
