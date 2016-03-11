#!/usr/bin/env bash
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

function help
{
  cat <<'eof'
Usage: plugin_archetype_deploy [option]

Deploys Gerrit plugin Maven archetypes to Maven Central

Valid options:
  --help                     show this message.
  --dry-run                  don't execute commands, just print them.

eof
exit
}

function getver
{
  grep "$1" $root/VERSION | sed "s/.*'\(.*\)'/\1/"
}

function instroot
{
  bindir=${0%/*}

  case $bindir in
  ./*) bindir=$PWD/$bindir ;;
  esac

  cd $bindir/..
  pwd
}

function doIt
{
  case $dryRun in
    true) echo "$@" ;;
    *) "$@" ;;
  esac
}

function build_and_deploy
{
  module=${PWD##*/}
  doIt mvn package gpg:sign-and-deploy-file \
    -Durl=$url \
    -DrepositoryId=sonatype-nexus-staging \
    -DpomFile=pom.xml \
    -Dfile=target/$module-$ver.jar
}

function run
{
  test ${dryRun:-'false'} == 'false'
  root=$(instroot)
  cd "$root"
  ver=$(getver GERRIT_VERSION)
  [[ $ver == *-SNAPSHOT ]] \
    && url="https://oss.sonatype.org/content/repositories/snapshots" \
    || url="https://oss.sonatype.org/service/local/staging/deploy/maven2"

  for d in gerrit-plugin-archetype \
           gerrit-plugin-js-archetype \
           gerrit-plugin-gwt-archetype ; do
    (cd "$d"; build_and_deploy)
  done
}

if [ "$1" == "--dry-run" ]; then
  dryRun=true && run
elif [ -z "$1" ]; then
  run
else
  help
fi
