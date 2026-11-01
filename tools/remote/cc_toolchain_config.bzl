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

"""Minimal C++ toolchain config for the Linux RBE platform.

Gerrit runs no C++ compile actions (protobuf ships a prebuilt protoc and the
Java toolchain uses a prebuilt ijar), so this toolchain is never invoked. It
exists only to satisfy analysis-time resolution of
@bazel_tools//tools/cpp:toolchain_type for the remote execution platform, and
is pinned to linux/x86_64 independent of the host so a non-Linux client (for
example macOS) can resolve it. The tool paths are placeholders and are never
executed. See tools/remote/BUILD and this upsream issue:
https://github.com/bazelbuild/bazel/issues/32316.
"""

load("@rules_cc//cc:cc_toolchain_config_lib.bzl", "tool_path")
load("@rules_cc//cc/common:cc_common.bzl", "cc_common")
load("@rules_cc//cc/toolchains:cc_toolchain_config_info.bzl", "CcToolchainConfigInfo")

_TOOL_PATHS = [
    tool_path(name = "gcc", path = "/usr/bin/gcc"),
    tool_path(name = "ld", path = "/usr/bin/ld"),
    tool_path(name = "ar", path = "/usr/bin/ar"),
    tool_path(name = "cpp", path = "/usr/bin/cpp"),
    tool_path(name = "gcov", path = "/usr/bin/gcov"),
    tool_path(name = "nm", path = "/usr/bin/nm"),
    tool_path(name = "objdump", path = "/usr/bin/objdump"),
    tool_path(name = "strip", path = "/usr/bin/strip"),
]

def _impl(ctx):
    return cc_common.create_cc_toolchain_config_info(
        ctx = ctx,
        toolchain_identifier = "rbe-linux-x86_64",
        host_system_name = "x86_64-unknown-linux-gnu",
        target_system_name = "x86_64-unknown-linux-gnu",
        target_cpu = "k8",
        target_libc = "glibc_2.39",
        compiler = "gcc",
        abi_version = "gcc",
        abi_libc_version = "glibc_2.39",
        tool_paths = _TOOL_PATHS,
    )

cc_toolchain_config = rule(
    implementation = _impl,
    attrs = {},
    provides = [CcToolchainConfigInfo],
)
