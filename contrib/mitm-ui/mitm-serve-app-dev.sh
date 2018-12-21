#!/bin/sh

workspace="./WORKSPACE"
if [[ ! -f ${workspace} ]] || [[ ! $(head -n 1 ${workspace}) == *"gerrit"* ]]; then
    echo Please change to cloned Gerrit repo from https://gerrit.googlesource.com/gerrit/
    exit 1
fi

mitm_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

${mitm_dir}/dev-chrome.sh &

${mitm_dir}/mitm-docker.sh "serve-app-dev.py --app $(pwd)/polygerrit-ui/app/"
