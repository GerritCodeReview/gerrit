#!/usr/bin/env bash
#
# Copyright (C) 2016 The Android Open Source Project
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
# A sceleton script to demonstrate how to use the preview_submit REST API call.
#
#

if test -z $server
then
        echo "The variable 'server' needs to point to your Gerrit instance"
        exit 1
fi

if test -z $changeId
then
        echo "The variable 'changeId' must contain a valid change Id"
        exit 1
fi

if test -z $gerrituser
then
        echo "The variable 'gerrituser' must contain a user/password"
        exit 1
fi

curl -u $gerrituser -w '%{http_code}' -o preview \
    $server/a/changes/$changeId/revisions/current/preview_submit?format=tgz >http_code
if ! grep 200 http_code >/dev/null
then
        # error out:
        echo "Error previewing submit $changeId due to:"
        cat preview
        echo
else
        # valid tgz file, extract and obtain a bundle for each project
        mkdir tmp-bundles
        (cd tmp-bundles && tar -zxf ../preview)
        for project in $(cd tmp-bundles && find -type f)
        do
                # Projects may contain slashes, so create the required
                # directory structure
                mkdir -p $(dirname $project)
                # $project is in the format of "./path/name/project.git"
                # remove the leading ./
                proj=${project:-./}
                git clone $server/$proj $proj

                # First some nice output:
                echo "Verify that the bundle is good:"
                GIT_WORK_TREE=$proj GIT_DIR=$proj/.git \
                        git bundle verify tmp-bundles/$proj
                echo "Checking that the bundle only contains one branch..."
                if test \
                    "$(GIT_WORK_TREE=$proj GIT_DIR=$proj/.git \
                    git bundle list-heads tmp-bundles/$proj |wc -l)" != 1
                then
                        echo "Submitting $changeId would affect the project"
                        echo "$proj"
                        echo "on multiple branches:"
                        git bundle list-heads
                        echo "This script does not demonstrate this use case."
                        exit 1
                fi
                # find the target branch:
                branch=$(GIT_WORK_TREE=$proj GIT_DIR=$proj/.git \
                    git bundle list-heads tmp-bundles/$proj | awk '{print $2}')
                echo "found branch $branch"
                echo "fetch the bundle into the repository"
                GIT_WORK_TREE=$proj GIT_DIR=$proj/.git \
                        git fetch tmp-bundles/$proj $branch
                echo "and checkout the state"
                git -C $proj checkout FETCH_HEAD
        done
        echo "Now run a test for all of: $(cd tmp-bundles && find -type f)"
fi
