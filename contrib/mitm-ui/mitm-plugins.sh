#!/bin/sh

if [[ -z "$1" ]]; then
    echo This script injects plugins for *.googlesource.com.
    echo Provide plugin paths, comma-separated, as a parameter.
    echo This script assumes files do not have dependencies, i.e. minified.
    exit 1
fi

realpath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

join () {
  local IFS="$1"
  shift
  echo "$*"
}

plugins=$1
plugin_paths=()
for plugin in $(echo ${plugins} | sed "s/,/ /g")
do
    plugin_paths+=($(realpath ${plugin}))
done

absolute_plugin_paths=$(join , "${plugin_paths[@]}")

mitm_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

${mitm_dir}/dev-chrome.sh &

bazel build //polygerrit-ui/app:test_components &

${mitm_dir}/mitm-docker.sh \
           "serve-app-dev.py \
           --plugins ${absolute_plugin_paths} \
           --strip_assets \
           --components $(pwd)/bazel-bin/polygerrit-ui/app/"
