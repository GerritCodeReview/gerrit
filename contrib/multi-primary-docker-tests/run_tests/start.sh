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

set -e
readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
source "$MYDIR"/lib_changes.sh

create_project() {
    ssh -p 29418 "$GERRIT_HOST_1" gerrit create-project \
        --empty-commit \
        --parent All-Projects \
        --owner "Administrators" \
        -- "$PROJECT"
}

update_permissions() {
    pushd "$USER_HOME"/workspace > /dev/null
    git clone ssh://"$GERRIT_HOST_1":29418/All-Projects
    cd All-Projects
    git config --replace-all -f  project.config access."refs/*".push "+force group Administrators"
    git config --replace-all -f project.config access."refs/*".create "group Administrators"
    git config -f project.config capability.createProject "group Administrators"
    git add . && git commit -m "project config update"
    git push origin HEAD:refs/meta/config
    popd > /dev/null
}

set_http_password() {
    local http_password=$(uuidgen)
    ssh -p 29418 "$GERRIT_HOST_1" gerrit set-account "$USER" --http-password "$http_password"
    for host in "${HOSTS[@]}" ; do
        echo "machine $host login $USER password $http_password" >> ~/.netrc
    done
}

create_test_user() {
    ssh-keygen -t rsa -b 4096 -C "" -N "" -f ~/.ssh/id_rsa &> /dev/null
    cat ~/.ssh/id_rsa.pub | ssh -p 29418 -i /gerrit_etc/ssh_host_rsa_key \
        "Gerrit Code Review@$GERRIT_HOST_1" suexec --as "admin@example.com" \
        -- gerrit create-account --ssh-key - --email "$USER_EMAIL" \
        --group "Administrators" "$USER"
}

HOSTS=("$GERRIT_HOST_1" "$GERRIT_HOST_2")
for host in "${HOSTS[@]}" ; do
    "$USER_RUN_TEST_DIR"/../wait-for-it.sh "$host":29418 --timeout=180 --strict
    "$USER_RUN_TEST_DIR"/../wait-for-it.sh "$host":8080 --timeout=180 --strict
done

git config --global user.name "$USER"
git config --global user.email "$USER_EMAIL"
PROJECT=test_proj

create_test_user
set_http_password
update_permissions
create_project
create_change_through_rest "$GERRIT_HOST_1" "$PROJECT"
sleep 15

"$USER_RUN_TEST_DIR"/push_to_primary1_review_on_primary2.sh "$GERRIT_HOST_1" \
    "$GERRIT_HOST_2" "$PROJECT"
