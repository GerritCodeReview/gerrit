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

set -eu

# Keep this version in sync with dev-contributing.txt.
VERSION="1.2"

root="$(git rev-parse --show-toplevel)"
if [[ -z "$root" ]]; then
  echo "google-java-format setup requires a git working tree"
  exit 1
fi

dir="$root/tools/format"

name="google-java-format-$VERSION-all-deps.jar"
url="https://github.com/google/google-java-format/releases/download/google-java-format-$VERSION/$name"
echo "Downloading google-java-format $VERSION from $url"
curl --create-dirs -Lo "$dir/$name" "$url"

launcher="$dir/google-java-format"
cat > "$launcher" <<EOF
#!/bin/bash -e
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

set -e

dir="\$(dirname "\$(readlink -f "\$(type -p "\$0")")")"
exec java -jar "\$dir/$name" "\$@"
EOF

chmod +x "$launcher"

cat <<EOF

Installed launcher script at $launcher
To set up an alias, add the following to your ~/.bashrc or equivalent:
  alias google-java-format='$launcher'
EOF
