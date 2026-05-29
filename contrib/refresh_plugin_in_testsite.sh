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

# This script compiles a Gerrit plugin whose name is passed as first parameter
# and copies it over to the plugin folder of the testsite. The path to the
# testsite needs to be provided by the variable GERRIT_TESTSITE or as second
# parameter.

SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$BASH_SOURCE")")
GERRIT_CODE_DIR="$SCRIPT_DIR/.."
cd "$GERRIT_CODE_DIR"

if [ "$#" -lt 1 ]
then
  echo "No plugin name provided as first argument. Stopping."
  exit 1
else
  PLUGIN_NAME="$1"
fi


if [ "$#" -lt 2 ]
then
  if [ -z ${GERRIT_TESTSITE+x} ]
  then
    echo "Path to local testsite is neiter set as GERRIT_TESTSITE nor passed as second argument. Stopping."
    exit 1
  fi
else
  GERRIT_TESTSITE="$2"
fi

if [ ! -d "$GERRIT_TESTSITE" ]
then
  echo "Testsite directory $GERRIT_TESTSITE does not exist. Stopping."
  exit 1
fi

bazel build //plugins/"$PLUGIN_NAME"/...
if [ $? -ne 0 ]
then
  echo "Building the $PLUGIN_NAME plugin failed"
  exit 1
fi

yes | cp -f "$GERRIT_CODE_DIR/bazel-genfiles/plugins/$PLUGIN_NAME/$PLUGIN_NAME.jar" "$GERRIT_TESTSITE/plugins/"
if [ $? -eq 0 ]
then
  echo "Plugin $PLUGIN_NAME copied successfully to testsite."
fi
