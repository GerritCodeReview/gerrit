#!/bin/sh

if [[ -z "$1" ]]; then
    echo This script serves one plugin with the rest of static content.
    echo Provide path to index plugin file, e.g. buildbucket.html for buildbucket plugin
    exit 1
fi

realpath() {
  OURPWD=$PWD
  cd "$(dirname "$1")"
  LINK=$(basename "$1")
  while [ -L "$LINK" ]; do
      LINK=$(readlink "$LINK")
      cd "$(dirname "$LINK")"
      LINK="$(basename "$1")"
  done
  REAL_DIR=`pwd -P`
  RESULT=$REAL_DIR/$LINK
  cd "$OURPWD"
  echo "$RESULT"
}

plugin=$(realpath $1)
plugin_root=$(dirname ${plugin})

mitm_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

${mitm_dir}/dev-chrome.sh &

bazel build //polygerrit-ui/app:test_components &

${mitm_dir}/mitm-docker.sh -v ${plugin_root}:${plugin_root} \
           "serve-app-dev.py \
           --plugins ${plugin} \
           --strip_assets \
           --plugin_root ${plugin_root}  \
           --components $(pwd)/bazel-bin/polygerrit-ui/app/"
