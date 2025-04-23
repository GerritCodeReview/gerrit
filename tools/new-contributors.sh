#!/bin/bash
#
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Check arguments
if [ "$#" -ne 2 ]; then
  echo "Small utility to help identify first time contributors to a specific version of Gerrit"
  echo "Usage: $0 <start-sha> <end-sha>"
  exit 1
fi

START_SHA=$1
END_SHA=$2

# Get authors before START_SHA
authors_before=$(git log --no-merges --format='%an' "$START_SHA" | sort | uniq)

# Get authors between START_SHA and END_SHA
authors_between=$(git log --no-merges --format='%an' "$START_SHA..$END_SHA" | sort | uniq)

# Find authors in between but not before
new_authors=$(comm -23 <(echo "$authors_between") <(echo "$authors_before"))

echo "New authors between $START_SHA and $END_SHA:"
echo "$new_authors"
