#!/bin/bash -e
#
# SPDX-FileCopyrightText: Copyright (c) 2024 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
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

#
# Best run from the Gerrit ref-updated hook
#

[ -z "$GIT_DIR" ] && echo "ERROR: GIT_DIR not set!"
[ -z "$GERRIT_SITE" ] && echo "ERROR: GERRIT_SITE not set!"

MYPROG=$(readlink -f -- "$BASH_SOURCE")
MYPATH=$(dirname -- "$MYPROG")

GERRIT_LOGS=$GERRIT_SITE/logs
GC_LOGS=$GERRIT_LOGS/gc

GERRIT_CONFIG=$GERRIT_SITE/etc/gerrit.config
REPOS=$(git config --file "$GERRIT_CONFIG" gerrit.basePath)
[[ "$REPOS" = "^/.*" ]] || REPOS=$GERRIT_SITE/$REPOS

"$MYPATH/event-driven-gc.sh" \
        --repos "$REPOS" \
        --logs "$GC_LOGS/event" \
        --semaphore "$GC_LOGS/event.semaphore" \
        2>> "$GERRIT_LOGS/gc_event_log"
