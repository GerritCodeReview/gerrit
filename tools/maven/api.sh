#!/usr/bin/env bash

# Copyright (C) 2015 The Android Open Source Project
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

set -e

if [[ "$#" -lt "1" ]] ; then
  cat <<EOF
Usage: run "$0 COMMAND [build_args...]" from the top of your workspace,
where COMMAND is one of

  install
  deploy
  war_install
  war_deploy

and build_args is the argument passed to the Bazel build command, e.g.,
  --config:java21

Set VERBOSE in the environment to get more information.

EOF

  exit 1
fi

set -o errexit
set -o nounset

case "$1" in
install)
    command="api_install"
    ;;
deploy)
    command="api_deploy"
    ;;
war_install)
    command="war_install"
    ;;
war_deploy)
    command="war_deploy"
    ;;
*)
    echo "unknown command $1"
    exit 1
    ;;
esac
shift

if [[ "${VERBOSE:-x}" != "x" ]]; then
  set -o xtrace
fi

if [[ `which bazelisk` ]]; then
  BAZEL_CMD=bazelisk
else
  BAZEL_CMD=bazel
fi

ROOT="$(pwd)"
STAGING_DIR="${ROOT}/tools/maven-central/staging-deploy"
M2_REPO="file://${HOME}/.m2/repository"

# All three API artifacts publish via rules_jvm_external's java_export `.publish`
# targets. Each stages jar + sources + javadoc + POM into MAVEN_REPO. GPG_SIGN is
# left false: for deploy, JReleaser signs the staged repository.
# API_RJE_JAR_ARTIFACT_PATHS is the matching staged repo-layout path under
# STAGING_DIR (used by the preflight, which asserts jar + sources + javadoc); add
# both entries when a new API jar migrates.
API_RJE_PUBLISH_TARGETS=(
  "//java/com/google/gerrit/extensions:gerrit-extension-api.publish"
  "//plugins:gerrit-plugin-api.publish"
  "//java/com/google/gerrit/acceptance:gerrit-acceptance-framework.publish"
)
API_RJE_JAR_ARTIFACT_PATHS=(
  "com/google/gerrit/gerrit-extension-api"
  "com/google/gerrit/gerrit-plugin-api"
  "com/google/gerrit/gerrit-acceptance-framework"
)

publish_rje() { # $1 = MAVEN_REPO url; remaining args = bazel build args
  local repo="$1"; shift
  local t
  for t in "${API_RJE_PUBLISH_TARGETS[@]}"; do
    MAVEN_REPO="${repo}" GPG_SIGN=false ${BAZEL_CMD} run "$@" "${t}"
  done
}

reset_staging() {
  rm -rf "${STAGING_DIR}"
  mkdir -p "${STAGING_DIR}"
}

# Preflight the staged API artifacts before JReleaser uploads: jreleaser.yml
# disables POM/source/javadoc verification (needed for the WAR), so assert here that
# each API artifact staged its POM + main/sources/javadoc jars and that no
# maven-metadata.xml survived the strip.
verify_rje_staged() {
  local rel base vdir v name a f
  for rel in "${API_RJE_JAR_ARTIFACT_PATHS[@]}"; do
    base="${STAGING_DIR}/${rel}"
    vdir="$(find "${base}" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1)"
    if [[ -z "${vdir}" ]]; then
      echo "error: ${rel} not staged under ${base}" >&2
      exit 1
    fi
    v="$(basename "${vdir}")"
    name="$(basename "${rel}")"
    a="${vdir}/${name}-${v}"
    for f in "${a}.pom" "${a}.jar" "${a}-sources.jar" "${a}-javadoc.jar"; do
      if [[ ! -f "${f}" ]]; then
        echo "error: expected staged artifact missing: ${f}" >&2
        exit 1
      fi
    done
  done
  if [[ -n "$(find "${STAGING_DIR}" -name 'maven-metadata.xml*' -print -quit)" ]]; then
    echo "error: stray maven-metadata.xml left in staging dir" >&2
    exit 1
  fi
}

# Upload the already-staged API repository to the Maven Central Portal via JReleaser.
# The release version is defined once in Starlark (//tools/maven:api_version); read
# it here instead of threading it through mvn.py. Credentials and GPG keys come from
# the JRELEASER_* environment set by the release job.
deploy_to_portal() { # remaining args = bazel build args
  ${BAZEL_CMD} build "$@" //tools/maven:api_version
  local version
  version="$(cat ./bazel-bin/tools/maven/api_version.txt)"
  env JRELEASER_MAVENCENTRAL_STAGE=UPLOAD jreleaser deploy \
    -c "${ROOT}/tools/maven-central/jreleaser.yml" \
    -D "jreleaser.project.version=${version}"
}

# The API artifacts are published directly via rules_jvm_external (no mvn.py). The
# WAR is still published through the generated mvn.py script until Phase 4.
case "${command}" in
api_install)
  publish_rje "${M2_REPO}" "$@"
  ;;
api_deploy)
  reset_staging
  publish_rje "file://${STAGING_DIR}" "$@"
  # rules_jvm_external writes maven-metadata.xml for file:/ repos; the Central
  # Portal generates its own and rejects unexpected files, so drop them before
  # JReleaser uploads the staged repository.
  find "${STAGING_DIR}" -name 'maven-metadata.xml*' -delete
  verify_rje_staged
  deploy_to_portal "$@"
  ;;
war_install|war_deploy)
  # WAR is still on the legacy generated mvn.py path (clears staging by default).
  ${BAZEL_CMD} build //tools/maven:gen_${command} "$@" || \
    { echo "${BAZEL_CMD} failed to build gen_${command}. Use VERBOSE=1 for more info" ; exit 1 ; }
  ./bazel-bin/tools/maven/${command}.sh "$@"
  ;;
esac
