#!/bin/bash
# Copyright (C) 2015 The Android Open Source Project
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

while [[ ! -f WORKSPACE && "$PWD" != / ]]; do
  cd ..
done
if [[ ! -f WORKSPACE ]]; then
  echo "$(basename "$0"): must be run from a gerrit checkout" 1>&2
  exit 1
fi

bazel build \
  polygerrit-ui:polygerrit_components.bower_components.zip \
  //polygerrit-ui:fonts.zip

cd polygerrit-ui/app
rm -rf bower_components
unzip -q ../../bazel-bin/polygerrit-ui/polygerrit_components.bower_components.zip
rm -rf fonts
unzip -q ../../bazel-bin/polygerrit-ui/fonts.zip -d fonts
cd ..
exec go run server.go "$@"
