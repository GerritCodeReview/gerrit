#!/bin/bash

# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [ $# -eq 0 ]; then
    set -- -h
fi
OPTIONS_SPEC="\
git subtree add   --name=<subtree-name> --prefix=<prefix> <ref>
git subtree merge <ref>
git subtree addremotes
git subtree rmremotes
git subtree fetch
git subtree subtrees
git subtree gitk
git subtree am
--
P,prefix=     the name of the subdir to split out
name=         the name of the subtree
"

# Find the root git dir; run git-sh-setup
up=`git rev-parse --show-cdup` || exit $?
cw=`git rev-parse --show-prefix` || exit $?
cd "${up}" 
. $(git --exec-path)/git-sh-setup
require_work_tree
cd "${cw}"

# Parse options
while [ $# -gt 0 ]; do
	opt="$1"
	shift
	case "$opt" in
		--name) name="$1"; shift ;;
		-P) prefix="$1"; shift ;;
		--) break ;;
		*) die "Unexpected option: $opt" ;;
	esac
done

cmd=$1
shift

# Resolve a ref to a SHA1
function resolve_sha1 {
	if [ -z "${1}" ]; then
		die "Ref not specified"
	fi
	sha1=`git-rev-list ${1} -n 1`
	if [ -z "${sha1}" ]; then
		die "Unable to resolve SHA1 for ${1}"
	fi
	echo ${sha1}
}

# Find the subtree id of the current directory
function get_dir_subtree_id {
	TOP_DIR=$(git rev-parse --show-cdup) || exit $?
	p=${1}
	p=${p%/}
	git config --file=${TOP_DIR}.gitsubtree \
		--get-regexp 'subtree\..*\.path' "^$p" |
	while read key path; do
		if [ "$p" = "$path" ]; then
			key=${key#subtree.}
			echo "${key/.path/}"
		fi
	done
}

# Find the subtree id of the current directory, searching parent directories
# as well.
function get_subtree_id {
	CUR_DIR=$(git rev-parse --show-prefix)
	ID=""
	while :
	do
		ID=$(get_dir_subtree_id ${CUR_DIR})
		if [ "${CUR_DIR}" == "`dirname ${CUR_DIR}`" ]; then
			die "Unable to determine subtree id"
		fi
		if [ "${ID}" != "" ]; then
			echo $ID
			exit 0
		fi
		CUR_DIR=`dirname ${CUR_DIR}`
	done
}

function msg_add {
	url=${1}
	refspec=${2}
	sha1=${3}
	name=${4}

	echo "Added subtree ${name}"
	echo
	echo "Sub-Tree: ${sha1} ${name}"
}

function cmd_add {
	url=$1
	refspec=$2

	cd_to_toplevel

	# Validate input
	if [ -z ${prefix} ]; then
		die "Prefix not specified"
	fi
	if [ -d ${prefix} ]; then
		die "Directory already exists: ${prefix}"
	fi

	if [ -z ${name} ]; then
		die "Subtree name not specified"
	fi
	if [ -n "`git-config --file .gitsubtree --get subtree.${name}.path`" ]; then
		die "Subtree \"${name}\" already exists"
	fi

	if [ -z ${refspec} ]; then
		refspec="refs/heads/master"
	fi

	# fetch upstream
	git fetch $url $refspec || exit $?
	sha1=$(resolve_sha1 FETCH_HEAD) || exit $?

	# merge in upstream
	git merge -s ours --no-commit FETCH_HEAD
	git read-tree --prefix=$prefix -u FETCH_HEAD

	# Update the .gitsubtree config file
	git config --file .gitsubtree subtree.${name}.path ${prefix}
	git config --file .gitsubtree subtree.${name}.url ${url}
	git add .gitsubtree

	# commit the change
	git commit -m "$(msg_add ${url} ${refspec} ${sha1} ${name})"
}

function msg_merge {
	refspec=${1}
	name=${2}
	sha1=${3}

	echo "Merged ${refspec} into subtree ${name}"
	echo
	echo "Sub-Tree: ${sha1} ${name}"
}

function cmd_merge {
	ref=${1}

	TOP_DIR=$(git rev-parse --show-cdup) || exit $?

	# Determine the subtree for the current dir
	subtree_id=$(get_subtree_id) || exit $?
	subtree_path=`git config --file ${TOP_DIR}.gitsubtree --get subtree."${subtree_id}".path`

	# Validate input
	sha1=$(resolve_sha1 ${ref}) || exit $?

	# Do the merge (TODO: specify subtree with Git 1.7+
	git merge -s subtree ${sha1} -m "$(msg_merge ${ref} ${subtree_id} ${sha1} )" || exit $?
}

function cmd_addremotes {
	TOP_DIR=$(git rev-parse --show-cdup) || exit $?
	git config --file=${TOP_DIR}.gitsubtree \
		--get-regexp 'subtree\..*\.url' |
	while read key url; do
		key=${key#subtree.}
		key=${key/.url/}
		git remote add ${key} ${url} &> /dev/null
	done
}

function cmd_rmremotes {
	TOP_DIR=$(git rev-parse --show-cdup) || exit $?
	git config --file ${TOP_DIR}.gitsubtree --get-regexp subtree.*.url |
	while read key url; do
		key=${key#subtree.}
		key=${key/.url/}
		git remote rm ${key} &> /dev/null
	done
}

function cmd_subtrees {
	git log --grep="Sub-Tree\:" | grep Sub-Tree |
	while read junk sha1 junk; do
		echo $sha1
	done
}

function cmd_fetch {
	cmd_addremotes
	git config --file .gitsubtree --get-regexp subtree.*.url | while read subtree url; do
		subtree=`echo $subtree | awk '{split($0,a,"."); print a[2]}'`
		git fetch ${url} --no-tags "refs/heads/*:refs/remotes/${subtree}/*"
	done
}

function cmd_gitk {
	if [ $# -eq 0 ]; then
		for i in $(cmd_subtrees); do echo ^$i; done | xargs gitk HEAD
	else
		for i in $(cmd_subtrees); do echo ^$i; done; echo $@ | xargs gitk
	fi
}

function cmd_am {
	TOP_DIR=$(git rev-parse --show-cdup) || exit $?
	subtree_id=$(get_subtree_id) || exit $?
	subtree_path=`git config --file ${TOP_DIR}.gitsubtree --get subtree."${subtree_id}".path`
	cd $(git rev-parse --show-cdup) || exit $?
	# --directory requires 1.6.1
	git am --directory=${subtree_path}
}

"cmd_$cmd" "$@"
