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
    local root="$(git rev-parse --show-toplevel)"
    if [[ -z "$root" ]]; then
        echo "google-java-format setup requires a git working tree"
        exit 1
    fi
    echo "$root/tools"
}

get_format_dir() {
    local tools_dir=$(get_tools_dir)
    echo $tools_dir/format 
}

get_name() {
    local version=$1
    echo "google-java-format-$version"
}

get_jar_name() {
    local version=$1
    local name=$(get_name $version)
    echo "$name-all-deps.jar"
}

setup_google_java_format() {
    local version=$1
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
    *)
        echo "unknown google-java-format version: $version"
        exit 1
    ;;
    esac

    local tools_dir=$(get_tools_dir)
    local format_dir=$(get_format_dir)

    mkdir -p "$format_dir"
    local jar_name=$(get_jar_name $version)
    local name=$(get_name $version)
    
    url="https://github.com/google/google-java-format/releases/download/$tag_prefix$version/$jar_name"
    "$tools_dir/download_file.py" -o "$format_dir/$jar_name" -u "$url" -v "$sha1"

    launcher="$format_dir/$name"

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
exec java -jar "\$dir/$name" "\$@"
EOF

    chmod +x "$launcher"

    cat <<EOF
Installed launcher script at $launcher
To set up an alias, add the following to your ~/.bashrc or equivalent:
  alias google-java-format='$launcher'
EOF
}

run_google_java_format() {
    echo "Not implemented"
}

SUPPORTED_VERSIONS=(1.7 1.22.0)
# Keep the default version in sync with dev-contributing.txt.
DEFAULT_VERSION="1.7"
HELP_TEXT="
    Usage:

        $0 [run|setup] [VERSION]
    
    Sets up or runs google-java-format of the specified version.
    Supported versions are \"${SUPPORTED_VERSIONS[*]}\".
    
"
    
VERSION=${2:-$DEFAULT_VERSION}
verify_version $VERSION
    
command=${1:-""}
case $command in
    run)
    run_google_java_format
    exit 0
    ;;
    setup)
    echo "Setting up Google Java Format $VERSION."
    setup_google_java_format $VERSION
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
