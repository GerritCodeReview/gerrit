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

if [[ "$#" == "0" ]] ; then
  cat <<EOF
Usage: run "$0 COMMAND" from the top of your workspace, where
COMMAND is one of

  api_install
  war_install
  api_deploy
  war_deploy

Set VERBOSE in the environment to get more information.

EOF

  exit 1
fi

set -o errexit
set -o nounset

command="$1"

case "${command}" in
api_install)
    ;;
api_deploy)
    ;;
war_install)
    ;;
war_deploy)
    ;;
*)
    echo "unknown command ${command}"
    exit 1
esac

if [[ -n "${VERBOSE:-}" ]]; then
  set -x
fi

buck build //tools/maven:gen_${command} || { echo "buck failed to build gen_${command}. Use VERBOSE=1 for more info" ; exit 1 }

script="./buck-out/gen/tools/maven/gen_${command}/${command}.sh"

# The PEX wrapper does some funky exit handling, so even if the script
# does "exit(0)", the return status is '1'. So we can't tell if the
# following invocation was successful.
${script}
