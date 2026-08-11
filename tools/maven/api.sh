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
  --config=java21

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

# Every Maven Central artifact publishes via rules_jvm_external. The three API jars
# use java_export; the WAR uses maven_export(target = release.war). Each `.publish`
# stages its files into MAVEN_REPO; GPG_SIGN is left false so that, for deploy,
# JReleaser signs the staged repository. API_RJE_JAR_ARTIFACT_PATHS is the staged
# repo-layout path per API jar (used by the preflight, which asserts jar + sources +
# javadoc); add both entries when a new API jar migrates.
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
WAR_RJE_PUBLISH_TARGET="//:gerrit-war.publish"
WAR_ARTIFACT_PATH="com/google/gerrit/gerrit-war"

# The complete set of artifacts a deploy is expected to publish. verify_release_targets
# fails the release if the .publish targets in the queried packages have drifted from
# this list, so a newly added (or removed) java_export/maven_export cannot silently
# change what ships. The query scope below covers where the four current artifacts
# live; extend it if a publish target is ever added elsewhere (e.g. a BOM under
# tools/maven).
EXPECTED_PUBLISH_TARGETS=(
  "${API_RJE_PUBLISH_TARGETS[@]}"
  "${WAR_RJE_PUBLISH_TARGET}"
)

verify_release_targets() {
  local expected actual
  expected="$(printf '%s\n' "${EXPECTED_PUBLISH_TARGETS[@]}" | sort -u)"
  # `maven_publish rule` is the generated rule behind java_export/maven_export
  # `.publish` (bazel query sees rule classes, not macro names). Scope to the
  # packages that own publish targets to avoid descending into the plugin/jgit
  # submodules. No build args are passed: target discovery does not need them, and
  # `bazel query --config=<build-only config>` (e.g. java21) would error. Do not
  # suppress query errors -- a broken query must fail the release loudly rather than
  # silently drop targets.
  if ! actual="$(${BAZEL_CMD} query \
      'kind("maven_publish rule", set(//java/... //plugins:all //:all))')"; then
    echo "error: bazel query for release targets failed" >&2
    exit 1
  fi
  actual="$(printf '%s\n' "${actual}" | sort -u)"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "error: release .publish targets drifted from api.sh's declared list:" >&2
    comm -13 <(echo "${expected}") <(echo "${actual}") | sed 's/^/  unlisted (in Bazel, not declared): /' >&2
    comm -23 <(echo "${expected}") <(echo "${actual}") | sed 's/^/  obsolete (declared, not in Bazel): /' >&2
    exit 1
  fi
}

# API_RJE_PUBLISH_TARGETS and API_RJE_JAR_ARTIFACT_PATHS describe the same API
# artifact set from two angles: publish targets for staging (publish_api) and staged
# repo-layout paths for verification (verify_api_staged). Guard against one being
# extended without the other: the drift check would still pass and the new artifact
# would publish, but its jar/sources/javadoc would go unchecked.
verify_api_list_shape() {
  if [[ ${#API_RJE_PUBLISH_TARGETS[@]} -ne ${#API_RJE_JAR_ARTIFACT_PATHS[@]} ]]; then
    echo "error: API_RJE_PUBLISH_TARGETS and API_RJE_JAR_ARTIFACT_PATHS differ in length" >&2
    exit 1
  fi
}

run_publish() { # $1 = MAVEN_REPO url; $2 = .publish target; rest = bazel build args
  local repo="$1" target="$2"; shift 2
  MAVEN_REPO="${repo}" GPG_SIGN=false ${BAZEL_CMD} run "$@" "${target}"
}

publish_api() { # $1 = MAVEN_REPO url; remaining args = bazel build args
  local repo="$1"; shift
  local t
  for t in "${API_RJE_PUBLISH_TARGETS[@]}"; do
    run_publish "${repo}" "${t}" "$@"
  done
}

reset_staging() {
  rm -rf "${STAGING_DIR}"
  mkdir -p "${STAGING_DIR}"
}

strip_metadata() {
  # rules_jvm_external writes maven-metadata.xml for file:/ repos; the Central
  # Portal generates its own and rejects unexpected files, so drop them before
  # JReleaser uploads the staged repository.
  find "${STAGING_DIR}" -name 'maven-metadata.xml*' -delete
}

verify_no_metadata() {
  if [[ -n "$(find "${STAGING_DIR}" -name 'maven-metadata.xml*' -print -quit)" ]]; then
    echo "error: stray maven-metadata.xml left in staging dir" >&2
    exit 1
  fi
}

# Preflight the staged API jars (jreleaser.yml disables its own POM/source/javadoc
# verification): assert each API artifact staged its POM + main/sources/javadoc jars.
verify_api_staged() {
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
  verify_no_metadata
}

# Preflight the staged WAR: only the .war and .pom (no sources/javadoc classifiers).
verify_war_staged() {
  local base vdir v a f
  base="${STAGING_DIR}/${WAR_ARTIFACT_PATH}"
  vdir="$(find "${base}" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1)"
  if [[ -z "${vdir}" ]]; then
    echo "error: gerrit-war not staged under ${base}" >&2
    exit 1
  fi
  v="$(basename "${vdir}")"
  a="${vdir}/gerrit-war-${v}"
  for f in "${a}.war" "${a}.pom"; do
    if [[ ! -f "${f}" ]]; then
      echo "error: expected staged artifact missing: ${f}" >&2
      exit 1
    fi
  done
  verify_no_metadata
}

# Fail fast before building/staging if the release credentials or the jreleaser
# binary are missing, rather than surfacing the problem mid-upload. jreleaser signs
# in-process from the in-memory JRELEASER_GPG_* keys (jreleaser.yml signing mode is
# MEMORY), so gpg itself is not required here -- CI exports the keys into the env.
preflight_release_env() {
  local missing=()
  [[ -n "${JRELEASER_MAVENCENTRAL_USERNAME:-}" ]] || missing+=("JRELEASER_MAVENCENTRAL_USERNAME")
  [[ -n "${JRELEASER_MAVENCENTRAL_TOKEN:-}" ]]    || missing+=("JRELEASER_MAVENCENTRAL_TOKEN")
  [[ -n "${JRELEASER_GPG_PUBLIC_KEY:-}" ]]        || missing+=("JRELEASER_GPG_PUBLIC_KEY")
  [[ -n "${JRELEASER_GPG_SECRET_KEY:-}" ]]        || missing+=("JRELEASER_GPG_SECRET_KEY")
  [[ -n "${JRELEASER_GPG_PASSPHRASE:-}" ]]        || missing+=("JRELEASER_GPG_PASSPHRASE")
  command -v jreleaser >/dev/null 2>&1            || missing+=("jreleaser (not on PATH)")
  if (( ${#missing[@]} )); then
    echo "error: missing release prerequisites: ${missing[*]}" >&2
    exit 1
  fi
}

# Upload the already-staged repository to the Maven Central Portal via JReleaser.
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

# Every artifact publishes via rules_jvm_external `.publish`; api.sh stages them and
# invokes JReleaser directly (no mvn.py). The API jars and the WAR are separate
# Portal deployments: each deploy verb resets the staging dir and uploads on its own.
case "${command}" in
api_install)
  publish_api "${M2_REPO}" "$@"
  ;;
api_deploy)
  preflight_release_env
  verify_release_targets
  verify_api_list_shape
  reset_staging
  publish_api "file://${STAGING_DIR}" "$@"
  strip_metadata
  verify_api_staged
  deploy_to_portal "$@"
  ;;
war_install)
  run_publish "${M2_REPO}" "${WAR_RJE_PUBLISH_TARGET}" "$@"
  ;;
war_deploy)
  preflight_release_env
  verify_release_targets
  reset_staging
  run_publish "file://${STAGING_DIR}" "${WAR_RJE_PUBLISH_TARGET}" "$@"
  strip_metadata
  verify_war_staged
  deploy_to_portal "$@"
  ;;
esac
