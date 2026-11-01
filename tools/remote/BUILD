load("@rules_cc//cc/toolchains:cc_toolchain.bzl", "cc_toolchain")
load(":cc_toolchain_config.bzl", "cc_toolchain_config")

platform(
    name = "platform",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    exec_properties = {
        "OSFamily": "Linux",
        "container-image": "docker://gcr.io/bazel-public/ubuntu2404@sha256:57bbaa84bec679736c53dcd1d326e8f835b3b9ce3e36c12a3c12a07eb59177d3",
    },
    visibility = ["//visibility:public"],
)

# Minimal C++ toolchain for the Linux RBE platform. Gerrit runs no C++ compile
# actions, so it is never invoked. It only has to satisfy analysis-time
# resolution of @bazel_tools//tools/cpp:toolchain_type for the remote platform,
# and is pinned to linux/x86_64 independent of the host so a non-Linux client
# (for example macOS) can resolve it. Enabled for remote builds only, via
# --extra_toolchains in tools/remote-bazelrc.
cc_toolchain_config(name = "cc_config")

filegroup(name = "empty")

cc_toolchain(
    name = "cc_compiler",
    all_files = ":empty",
    ar_files = ":empty",
    compiler_files = ":empty",
    dwp_files = ":empty",
    linker_files = ":empty",
    objcopy_files = ":empty",
    strip_files = ":empty",
    toolchain_config = ":cc_config",
)

toolchain(
    name = "cc-toolchain",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    toolchain = ":cc_compiler",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
    visibility = ["//visibility:public"],
)
