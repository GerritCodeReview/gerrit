#!/bin/sh

if [[ -z "$1" ]]; then
    echo This script forces or replaces default site theme on *.googlesource.com
    echo Provide path to the theme.html as a parameter.
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

theme=$(realpath "$1")
theme_dir=$(dirname "${theme}")

mitm_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

"${mitm_dir}"/dev-chrome.sh &

"${mitm_dir}"/mitm-docker.sh -v "${theme_dir}":"${theme_dir}" "serve-app-dev.py --strip_assets --theme \"${theme}\""
