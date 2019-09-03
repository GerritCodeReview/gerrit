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

set +e

GERRIT_GIT_DIR=$1
shift

ALL_PROJECTS=$@
if [[ "$2" -eq "ALL" ]] ; then
 ALL_PROJECTS=`ssh -p 29418 localhost  gerrit ls-projects --all`
fi

ALL_PROJECTS_ARRAY=(${ALL_PROJECTS// / })

for project in "${ALL_PROJECTS_ARRAY[@]}"
do
	if [ "$project" = "All-Users" ]; then
		echo "Skipping $project ..."
	else
		echo "Removing meta ref for $project ..."
		projectDir="$GERRIT_GIT_DIR/$project".git
		cd $projectDir
		git show-ref | grep "/meta$"
		if [ $? -eq 0 ]; then
			git show-ref | grep "/meta$" | cut -d' ' -f2 | xargs  -l git update-ref -d
		fi
	fi
done

echo "Remove sequence ref"
allProjectDir="$GERRIT_GIT_DIR/All-Projects.git"
cd $allProjectDir
git update-ref -d refs/sequences/changes
