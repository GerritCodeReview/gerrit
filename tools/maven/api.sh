#!/usr/bin/env bash

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

set -e

if [[ "$#" -lt "1" ]] ; then
  cat <<EOF
Usage: run "$0 COMMAND [build_args...]" from the top of your workspace,
where COMMAND is one of

  install
  deploy
  war_install
  war_deploy

Set VERBOSE in the environment to get more information.

EOF

  exit 1
fi

set -o errexit
set -o nounset

case "$1" in
install)
    command="api_install"
    ;;
deploy)
    command="api_deploy"
    ;;
war_install)
    command="war_install"
    ;;
war_deploy)
    command="war_deploy"
    ;;
*)
    echo "unknown command $1"
    exit 1
    ;;
esac
shift

if [[ "${VERBOSE:-x}" != "x" ]]; then
  set -o xtrace
fi

if [[ `which bazelisk` ]]; then
  BAZEL_CMD=bazelisk
else
  BAZEL_CMD=bazel
fi

bazel build //tools/maven:gen_${command} "$@" || \
  { echo "bazel failed to build gen_${command}. Use VERBOSE=1 for more info" ; exit 1 ; }

./bazel-bin/tools/maven/${command}.sh
