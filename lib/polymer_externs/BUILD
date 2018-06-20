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

package(
    default_visibility = ["//visibility:public"],
)

load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_library")

genrule(
    name = "polymer_closure_renamed",
    srcs = ["@polymer_closure//file"],
    outs = ["polymer_closure_renamed.js"],
    cmd = "cp $< $@",
)

closure_js_library(
    name = "polymer_closure",
    srcs = [":polymer_closure_renamed"],
    data = ["//lib:LICENSE-Apache2.0"],
    no_closure_library = True,
)
