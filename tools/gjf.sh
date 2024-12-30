#!/bin/bash
#
# Copyright (C) 2024 The Android Open Source Project
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

set -eu

verify_version() {
    local version=$1
    if [[ ! " ${SUPPORTED_VERSIONS[*]} " =~ "$version " ]]
      then
        echo "Unknown version: $version."
        echo ""
        echo "$HELP_TEXT"
        exit 1
    fi
}

get_tools_dir() {
    # Set root relative to this script's location.
    local root="$(git -C $(dirname $0) rev-parse --show-toplevel)"
    if [[ -z "$root" ]]; then
        echo "google-java-format setup requires a git working tree"
        exit 1
    fi
    echo "$root/tools"
}

get_format_dir() {
    local format_dir=$(get_tools_dir)/format
    # Format directory is not guaranteed to exist, create if missing.
    mkdir -p "$format_dir"
    echo $format_dir
}

get_gjf_name() {
    local version=$1
    echo "google-java-format-$version"
}

get_jar_name() {
    local version=$1
    echo $(get_gjf_name $version)-all-deps.jar
}

get_jar_location() {
    local version=$1
    echo $(get_format_dir)/$(get_jar_name $version)
}

get_launcher_location() {
    local version=$1
    echo $(get_format_dir)/$(get_gjf_name $version)
}

setup_google_java_format() {
    local version=$1

    if [ -f $(get_jar_location $version) ] && [ -f $(get_launcher_location $version) ]; then
        echo "Google-Java-Format $version already set up.!"
        return 0
    fi

    echo "Setting up Google Java Format $VERSION."

    local sha1
    local tag_prefix

    case "$version" in
    1.7)
        sha1="b6d34a51e579b08db7c624505bdf9af4397f1702"
        tag_prefix=google-java-format-
        ;;
    1.22.0)
        sha1="693d8fd04656886a2287cfe1d7a118c4697c3a57"
        tag_prefix=v
        ;;
    1.24.0)
        sha1="3b55f08a70d53984ac4b3e7796dc992858d6bdd8"
        tag_prefix=v
    ;;
    *)
        echo "unknown google-java-format version: $version"
        exit 1
    ;;
    esac

    url="https://github.com/google/google-java-format/releases/download/$tag_prefix$version/$(get_jar_name $version)"
    "$(get_tools_dir)/download_file.py" -o "$(get_jar_location $version)" -u "$url" -v "$sha1"

    launcher="$(get_launcher_location $version)"

    cat > "$launcher" <<EOF
#!/bin/bash
#
# Copyright (C) 2017 The Android Open Source Project
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

function abs_script_dir_path {
    SOURCE=\${BASH_SOURCE[0]}
    while [ -h "\$SOURCE" ]; do
      DIR=\$( cd -P \$( dirname "\$SOURCE") && pwd )
      SOURCE=\$(readlink "\$SOURCE")
      [[ \$SOURCE != /* ]] && SOURCE="\$DIR/\$SOURCE"
    done
    DIR=\$( cd -P \$( dirname "\$SOURCE" ) && pwd )
    echo \$DIR
}

set -e

dir="\$(abs_script_dir_path "\$0")"
exec java -jar "\$dir/$(get_jar_name $version)" "\$@"
EOF

    chmod +x "$launcher"

    cat <<EOF
Installed launcher script at $launcher
To set up an alias, add the following to your ~/.bashrc or equivalent:
  alias google-java-format='$launcher'
EOF
}

run_google_java_format() {
    local version=$1
    echo 'Running google-java-format check...'
    git show --diff-filter=AM --name-only --pretty="" HEAD | grep java$ | xargs $(get_launcher_location $version) -r
}

# MAIN

HELP_TEXT="
    Usage:

        $0 [run|setup] [VERSION]
        $0 [default-version]

    Sets up or runs google-java-format of the specified version or returns the default version.
    Supported versions are \"${SUPPORTED_VERSIONS[*]}\".

"

SUPPORTED_VERSIONS=(1.7 1.22.0 1.24.0)
# Keep the default version in sync with dev-contributing.txt.
DEFAULT_VERSION="1.24.0"
VERSION=${2:-$DEFAULT_VERSION}
verify_version $VERSION

command=${1:-""}
case $command in
    run)
    setup_google_java_format $VERSION
    run_google_java_format $VERSION
    exit 0
    ;;
    setup)
    setup_google_java_format $VERSION
    exit 0
    ;;
    default-version)
    echo $DEFAULT_VERSION
    exit 0
    ;;
    -h|--help)
    echo "$HELP_TEXT"
    exit 1
    ;;
    *)
      echo "Unknown command \"$command\""
      echo ""
      echo "$HELP_TEXT"
      exit 1
    ;;
esac
