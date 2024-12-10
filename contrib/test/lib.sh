#
# SPDX-FileCopyrightText: Copyright (c) 2024 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
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

# This library is only meant for use as helpers from this directory as the function
# names are not namespaced and likely will change their behavior/interfaces at will.

q() { "$@" > /dev/null 2>&1 ; }

create_commit() {
	COMMIT_NUMBER=$(($COMMIT_NUMBER + 1))
	(
		cd "$REPO"
		echo "$COMMIT_NUMBER" > "my_test_file"
		q git add .
		q git commit -m "$COMMIT_NUMBER"
	)
}
