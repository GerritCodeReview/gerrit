#!/bin/bash

if [ $# -eq 0 ]; then
    set -- -h
fi
OPTS_SPEC="\
git subtree add   --name=<subtree-name> --prefix=<prefix> <ref>
git subtree merge <ref>
--
P,prefix=     the name of the subdir to split out
name=         the name of the subtree
"
eval "$(echo "$OPTS_SPEC" | git rev-parse --parseopt -- "$@" || echo exit $?)"

# Find the root .git dir
CUR_DIR=`pwd`
TOP_DIR="${CUR_DIR}"
while [ ! -f "${TOP_DIR}/.git" ] && [ ! -d "${TOP_DIR}/.git" ]; do
	TOP_DIR=`dirname ${TOP_DIR}`
	if [ "`dirname ${TOP_DIR}`" == "${TOP_DIR}" ]; then
		echo "Not a valid git directory";
		exit 1
	fi
done

# Set up git functions
cd ${TOP_DIR}
PATH=$PATH:$(git --exec-path)
. git-sh-setup
cd ${CUR_DIR}

require_work_tree

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
	git config --file ${TOP_DIR}/.gitsubtree --get-regexp subtree.*.path |
	while read subtree path; do
		subtree=`echo $subtree | awk '{split($0,a,"."); print a[2]}'`
		if [ "${TOP_DIR}/${path}" == "${CUR_DIR}" ]; then
			echo ${subtree}
		fi
	done
}

# Find the subtree id of the current directory, searching parent directories
# as well.
function get_subtree_id {
	TOP_DIR=`dirname $GIT_DIR`
	CUR_DIR=`pwd`
	ID=""
	while :
	do
		ID=$(get_dir_subtree_id)
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

	echo "\${prefix}=${prefix}"
	echo "\${name}=${name}"
	echo "\${url}=${url}"
	echo "\${refspec}=${refspec}"

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

	# Determine the subtree for the current dir
	subtree_id=$(get_subtree_id) || exit $?
	subtree_path=`git config --file ${TOP_DIR}/.gitsubtree --get subtree."${subtree_id}".path`

	# Validate input
	sha1=$(resolve_sha1 ${ref}) || exit $?

	# Do the merge (TODO: specify subtree with Git 1.7+
	git merge -s subtree ${sha1} || exit $?
	git commit --amend -m "$(msg_merge ${ref} ${subtree_id} ${sha1} )" || exit $?
}

function cmd_addremotes {
	cmd_rmremotes
	git config --file ${TOP_DIR}/.gitsubtree --get-regexp subtree.*.url |
	while read subtree url; do
		subtree=`echo $subtree | awk '{split($0,a,"."); print a[2]}'`
		git remote add ${subtree} ${url}
	done
}

function cmd_rmremotes {
	git config --file ${TOP_DIR}/.gitsubtree --get-regexp subtree.*.url |
	while read subtree url; do
		subtree=`echo $subtree | awk '{split($0,a,"."); print a[2]}'`
		git remote rm ${subtree} &> /dev/null
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
	subtree_id=$(get_subtree_id) || exit $?
	subtree_path=`git config --file ${TOP_DIR}/.gitsubtree --get subtree."${subtree_id}".path`
	pushd ${TOP_DIR} > /dev/null
	# --directory requires 1.6.1
	git am --directory=${subtree_path}
	popd > /dev/null
}

"cmd_$cmd" "$@"
