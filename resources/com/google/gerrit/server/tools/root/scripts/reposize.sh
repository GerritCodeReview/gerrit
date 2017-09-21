#!/usr/bin/env bash
#
# Copyright (C) 2014 The Android Open Source Project
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
# Show n largest objects in a git repo's pack files.
#
# usage:
#      $ reposize.sh 100		# find and list biggest 100 objects
#
# derived from
# http://stubbisms.wordpress.com/2009/07/10/git-script-to-show-largest-pack-objects-and-trim-your-waist-line/

if [ ! $# == 1 ]; then
    echo "
    Usage: $0 <number of biggest objects to show>
        if there are loose objects the script will run 'git gc' to move all data to packs
"
    exit
fi

# find git repository directory
gitdir=$(git rev-parse --git-dir 2>.error.log)
if [ $? -ne 0 ]; then
    echo $(cat .error.log)
    rm .error.log
    exit
fi
rm .error.log

object_count=$(git count-objects -v | grep count: | cut -f 2 -d ' ')
if [ $object_count -gt 1 ]; then
    echo "-------------------------------------------------------"
    echo "$object_count loose objects found in repository $gitdir"
    echo "-> running git gc to move all data to packs"
    git gc
    echo "-------------------------------------------------------"
fi

# set the internal field separator to line break, so that we can iterate easily over the verify-pack output
IFS=$'\n';

# list all objects including their size, sort by size, take top $1 biggest blobs
objects=$(git verify-pack -v $gitdir/objects/pack/pack-*.idx | grep -v chain | sort -k3nr | head -n $1)

echo "All sizes are in kiB's. The pack column is the size of the object, compressed, inside the pack file."

output="size,pack,SHA,location"
for y in $objects
do
    # extract the size in bytes
    size=$(($(echo $y | cut -f 5 -d ' ') / 1024))
    # extract the compressed size in bytes
    compressedSize=$(($(echo $y | cut -f 6 -d ' ') / 1024))
    # extract the SHA
    sha=$(echo $y | cut -f 1 -d ' ')
    # find the objects location in the repository tree
    other=$(git rev-list --all --objects | grep $sha)
    output="${output}\n${size},${compressedSize},${other}"
done

echo -e $output | column -t -s ', '
