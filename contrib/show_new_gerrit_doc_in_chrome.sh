#!/bin/bash
#
# Copyright (C) 2019 The Android Open Source Project
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

# This script builds Gerrit's documentation and shows the current state in
# Chrome. Specific pages (e.g. rest-api-changes.txt) including anchors can be
# passed as parameter to jump directly to them.

SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$BASH_SOURCE")")
GERRIT_CODE_DIR="$SCRIPT_DIR/.."
cd "$GERRIT_CODE_DIR"

bazel build Documentation:searchfree
if [ $? -ne 0 ]
then
  echo "Building the documentation failed. Stopping."
  exit 1
fi

TMP_DOCS_DIR=/tmp/gerrit_docs
rm -rf "$TMP_DOCS_DIR"
unzip bazel-bin/Documentation/searchfree.zip -d "$TMP_DOCS_DIR" </dev/null >/dev/null 2>&1 & disown
if [ $? -ne 0 ]
then
  echo "Unzipping the documentation to $TMP_DOCS_DIR failed. Stopping."
  exit 1
fi

if [ "$#" -lt 1 ]
then
  FILE_NAME="index.html"
else
  FILE_NAME="$1"
fi
DOC_FILE_NAME="${FILE_NAME/.txt/.html}"
google-chrome "file:///$TMP_DOCS_DIR/Documentation/$DOC_FILE_NAME" </dev/null >/dev/null 2>&1 & disown
