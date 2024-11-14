#!/bin/bash
#
# Copyright (C) 2024 The Android Open Source Project
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

set -eu

GJF_VERSION=$(grep -o "^VERSION=.*$" tools/setup_gjf.sh | grep -o '[0-9][0-9]*\.[0-9][0-9]*[\.0-9]*')
GJF="tools/format/google-java-format-$GJF_VERSION"
if [ ! -f "$GJF" ]; then
  tools/setup_gjf.sh
  GJF=$(find 'tools/format' -regex '.*/google-java-format-[0-9][0-9]*\.[0-9][0-9]*')
fi
echo 'Running google-java-format check...'
git show --diff-filter=AM --name-only --pretty="" HEAD | grep java$ | xargs $GJF -r
