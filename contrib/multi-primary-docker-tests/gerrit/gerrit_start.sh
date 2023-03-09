#!/usr/bin/env bash
#
# Copyright (C) 2023 The Android Open Source Project
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

die() { echo -e "\nERROR: $@" ; exit 1 ; } # error_message

[ -n "$WAIT_FOR" ] && "$DOCKER_DIR"/wait-for-it.sh "$WAIT_FOR" --timeout=180 --strict
sleep 10

cp "$DOCKER_DIR"/config/gerrit.config "$GERRIT_SITE"/etc/gerrit.config
cp "$DOCKER_DIR"/config/jgit.config "$GERRIT_SITE"/etc/jgit.config
cp "$DOCKER_DIR"/config/secure.config "$GERRIT_SITE"/etc/secure.config
mkdir -p "$GERRIT_SITE/etc" && chmod 777 "$GERRIT_SITE/etc"

echo "gerrit" | sudo -S mount -o nolock -t nfs -o rsize=32768,wsize=32768,intr,noatime,tcp \
    nfs-server:/var/git-data "$GERRIT_SITE"/git
[ $? -ne 0 ] && die "Unable to mount nfs-server:/var/git-data"

java -jar "$GERRIT_SITE"/bin/gerrit.war init -d "$GERRIT_SITE" --batch
chown -R "$GERRIT_USER"

echo "Running Gerrit ..."
"$GERRIT_SITE"/bin/gerrit.sh run
