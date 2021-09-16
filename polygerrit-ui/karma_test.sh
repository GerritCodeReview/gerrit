#!/bin/bash

set -euo pipefail
./$1 start $2 \
  --root 'polygerrit-ui/app/_pg_with_tests_out/**/' \
  --test-files '*_test.js'
