# Copyright (C) 2026 The Android Open Source Project
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

"""Helpers for the EE8/EE10 (javax/jakarta servlet) flavour split."""

# `target_compatible_with` value that makes a target buildable ONLY in the EE10
# (jakarta.servlet) flavour. In the default (EE8) configuration the target is
# marked incompatible, so wildcard builds and tests (`bazel build //...`,
# `bazel test //...`) **skip** it instead of failing to compile its jakarta
# sources against the javax/Guice-6 tier. Build/run it under
# `--@com_googlesource_gerrit_bazlets//flags:flavour=ee10` (where it is
# compatible) or via a self-transitioning `-ee10` target. This is the conditional
# alternative to a `manual` tag: `manual` would hide the target from the EE10
# wildcard pass too, whereas this keeps it visible exactly when the flavour is
# active.
EE10_ONLY = select({
    "//tools:ee10": [],
    "//conditions:default": ["@platforms//:incompatible"],
})
