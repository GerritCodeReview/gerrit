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

NEW_SCRIPT=$(dirname $0)/gjf.sh
VERSION=${1:-""}

echo
echo "WARNING:"
echo "  Calling $0 is deprecated and $0 will be removed in 3.13.
echo "  Call \"$NEW_SCRIPT run $VERSION\" instead"."
echo

$NEW_SCRIPT run $VERSION
