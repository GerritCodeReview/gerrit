#!/usr/bin/env bash
#
# Copyright (C) 2023 The Android Open Source Project
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

readlink -f / &> /dev/null || readlink() { greadlink "$@" ; } # for MacOS
MYDIR=$(dirname -- "$(readlink -f -- "$0")")
ARTIFACTS=$MYDIR/gerrit/artifacts

die() { echo -e "\nERROR: $@" ; kill $$ ; exit 1 ; } # error_message

progress() { # message cmd [args]...
    message=$1 ; shift
    echo -n "$message"
    "$@" &
    pid=$!
    while kill -0 $pid 2> /dev/null ; do
        echo -n "."
        sleep 2
    done
    echo
    wait "$pid"
}

usage() { # [error_message]
    local prog=$(basename -- "$0")
    cat <<EOF
Usage:
    "$prog" --gerrit-war <WAR URL or file path> \
            [--plugin|-p <plugin name> <plugin JAR URL or file path> ...] \
            [--module <module name> <module JAR URL or file path> ...] \
            [--preserve] [--logs-file <file>]


    --help|-h
    --gerrit-war                gerrit WAR URL or the file path in local workspace
                                eg: file:///path/to/gerrit.war
    --plugin                    plugin name and path to plugin JAR file. This
                                switch can be passed multiple times
    --module                    module name and path to module JAR file. This
                                switch can be passed multiple times
    --preserve                  To preserve the docker setup for debugging
    --logs-file                 File to which the full logs are written

    NOTE: index-elasticsearch module is mandatory
EOF

    [ -n "$1" ] && die "$1"
    exit 0
}

check_prerequisite() {
    docker --version > /dev/null || die "docker is not installed"
    docker-compose --version > /dev/null || die "docker-compose is not installed"
    [[ "$OSTYPE" == "darwin"* ]] && die "multi primary core tests are not yet supported on MacOS"
}

fetch_artifact() { # source_location output_path
    if [[ "$1" =~ ^file://|^http://|^https:// ]] ; then
        curl --silent --fail --netrc "$1" --output "$2" --create-dirs || die "unable to fetch $1"
    else
        cp -f "$1" "$2"
    fi
}

fetch_artifacts() {
    local plugin_name module_name
    mkdir -p "$ARTIFACTS"/bin "$ARTIFACTS"/plugins "$ARTIFACTS"/lib
    [ -n "$GERRIT_WAR" ] && fetch_artifact "$GERRIT_WAR" "$ARTIFACTS/bin/gerrit.war"
    for plugin_name in "${!PLUGIN_JAR_BY_NAME[@]}" ; do
        if [ -n "${PLUGIN_JAR_BY_NAME["$plugin_name"]}" ] ; then
            fetch_artifact "${PLUGIN_JAR_BY_NAME[$plugin_name]}" \
                "$ARTIFACTS/plugins/$plugin_name.jar"
        fi
    done
    for module_name in "${!MODULE_JAR_BY_NAME[@]}" ; do
        if [ -n "${MODULE_JAR_BY_NAME["$module_name"]}" ] ; then
            fetch_artifact "${MODULE_JAR_BY_NAME[$module_name]}" \
                "$ARTIFACTS/lib/$module_name.jar"
        fi
    done
}

build_images() {
    docker-compose "${COMPOSE_ARGS[@]}" build "${BUILD_ARGS[@]}" &> "$LOGS_FILE"
    rm -rf "$ARTIFACTS"
}

run_tests() {
    docker-compose "${COMPOSE_ARGS[@]}" up --detach
    docker-compose "${COMPOSE_ARGS[@]}" exec -T --user="gerrit_admin" \
        run_tests bash -c "/docker/run_tests/start.sh"
}

get_run_test_container() {
    docker-compose "${COMPOSE_ARGS[@]}" ps | grep run_tests | awk '{ print $1 }'
}

cleanup() {
    docker-compose "${COMPOSE_ARGS[@]}" logs -t | sort -u -k 3 &>> "$LOGS_FILE"
    echo "Complete logs can be found at $LOGS_FILE"
    if [ "$PRESERVE" = "true" ] ; then
        echo "Preserving the following docker setup"
        docker-compose "${COMPOSE_ARGS[@]}" ps
        echo ""
        echo "To exec into runtests container, use following command:"
        echo "docker exec -it $(get_run_test_container) /bin/bash"
        echo ""
        echo "Run the following command to bring down the setup:"
        echo "docker-compose ${COMPOSE_ARGS[@]} down -v --rmi local"
    else
        echo "Bringing down docker setup"
        docker-compose "${COMPOSE_ARGS[@]}" down -v --rmi local 2>/dev/null
    fi
}

PRESERVE="false"
declare -A PLUGIN_JAR_BY_NAME MODULE_JAR_BY_NAME
PROJECT_NAME="mp_tests_$$"
COMPOSE_YAML="$MYDIR/docker-compose.yaml"
COMPOSE_ARGS=(--project-name "$PROJECT_NAME" -f "$COMPOSE_YAML")
LOGS_FILE="/tmp/${PROJECT_NAME}_full_logs.log"
check_prerequisite
while (( "$#" )); do
    case "$1" in
        --help|-h)                    usage ;;
        --gerrit-war)                 shift ; GERRIT_WAR=$1 ;;
        --plugin)                     shift ; PLUGIN_JAR_BY_NAME["$1"]=$2 ; shift ;;
        --module)                     shift ; MODULE_JAR_BY_NAME["$1"]=$2 ; shift ;;
        --logs-file)                  shift ; LOGS_FILE=$1 ;;
        --preserve)                   PRESERVE="true" ;;
        *)                            usage "invalid argument $1" ;;
    esac
    shift
done

[ -n "$GERRIT_WAR" ] || usage "'--gerrit-war' not set"
touch "$LOGS_FILE"
[ -e "$LOGS_FILE" ] || usage "Provide a valid logs file"

progress "Fetching artifacts" fetch_artifacts
[ -s "$ARTIFACTS/lib/index-elasticsearch.jar" ] || usage "required index-elasticsearch module is not set"

( trap cleanup EXIT SIGTERM
    progress "Building docker images" build_images
    run_tests
)
