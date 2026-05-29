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

# This script starts the local testsite in debug mode. If the flag "-u" is
# passed, Gerrit is built from the current state of the repository and the
# testsite is refreshed. The path to the testsite needs to be provided by
# the variable GERRIT_TESTSITE or as parameter (after any used flags).
# The testsite can be stopped by interrupting this script.

SCRIPT_DIR=$(dirname -- "$(readlink -f -- "$BASH_SOURCE")")
GERRIT_CODE_DIR="$SCRIPT_DIR/.."
cd "$GERRIT_CODE_DIR"

UPDATE=false
while getopts ':u' flag; do
  case "${flag}" in
    u) UPDATE=true ;;
  esac
done
shift $(($OPTIND-1))

if [ "$#" -lt 1 ]
then
  if [ -z ${GERRIT_TESTSITE+x} ]
  then
    echo "Path to local testsite is neither set as GERRIT_TESTSITE nor passed as first argument. Stopping."
    exit 1
  fi
else
  GERRIT_TESTSITE="$1"
fi

if [ "$UPDATE" = true ]
then
  echo "Refreshing testsite"
  bazel build gerrit
  if [ $? -ne 0 ]
  then
    echo "Build failed. Stopping."
    exit 1
  fi
  $(bazel info output_base)/external/local_jdk/bin/java -jar bazel-bin/gerrit.war init --batch -d "$GERRIT_TESTSITE"
  if [ $? -ne 0 ]
  then
    echo "Patching the testsite failed. Stopping."
    exit 1
  fi
fi

$(bazel info output_base)/external/local_jdk/bin/java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 bazel-bin/gerrit.war daemon -d "$GERRIT_TESTSITE" --console-log
