# Copyright (C) 2016 The Android Open Source Project
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

# Re-export junit_tests from bazlets. It is a strict superset of the previous
# in-tree macro (same generated @RunWith(Suite) logic) and adds the suite_srcs
# parameter, which is required to run tests compiled from a generated .srcjar
# (e.g. the servlet-flavour-transformed EE10 test sources). Existing srcs-only
# callers are unaffected.
load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", _junit_tests = "junit_tests")

junit_tests = _junit_tests
