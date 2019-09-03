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
#!/usr/bin/env bash

set -e

if [[ "$#" -lt "2" ]] ; then
  cat <<EOF
Usage: run "$0 /path/to/git/dir [project...]" or "$0 /path/to/git/dir ALL"

This util script can be used in case of rollback to ReviewDB during an unsuccessful
migration to NoteDB or simply while testing the migration process.

It will remove all the refs used by NoteDB added during the migration (i.e.: change meta refs and sequence ref).
EOF
  exit 1
fi

GERRIT_GIT_DIR=$1
shift

ALL_PROJECTS=$@
if [[ "$2" -eq "ALL" ]] ; then
 ALL_PROJECTS=`ssh -p 29418 localhost  gerrit ls-projects --all --format JSON | grep '"id": ' | cut -d'"' -f 4`
fi

ALL_PROJECTS_ARRAY=(${ALL_PROJECTS// / })

urldecode() {
    # urldecode <string>
    local url_encoded="${1//+/ }"
    printf '%b' "${url_encoded//%/\\x}"
}

for project in "${ALL_PROJECTS_ARRAY[@]}"
do
	decodedProject=`urldecode $project`
	if [ "$decodedProject" = "All-Users" ]; then
		echo "Skipping $decodedProject ..."
	else
		echo "Removing meta ref for $decodedProject ..."
		projectDir="$GERRIT_GIT_DIR/$decodedProject".git
		cd "$projectDir"
		if [ `git show-ref meta | grep -q "/meta$"` ]; then
			git show-ref meta | cut -d' ' -f2 | xargs  -l git update-ref -d
		fi
	fi
done

echo "Remove sequence ref"
allProjectDir="$GERRIT_GIT_DIR/All-Projects.git"
cd $allProjectDir
git update-ref -d refs/sequences/changes
