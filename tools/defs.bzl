"""
Bazel definitions for tools.
"""

load("@bazel_features//:deps.bzl", "bazel_features_deps")
load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies")
load("@toolchains_protoc//protoc:repositories.bzl", "rules_protoc_dependencies")
load("@toolchains_protoc//protoc:toolchain.bzl", "protoc_toolchains")

def gerrit_init():
    """
    Initialize the WORKSPACE for gerrit targets
    """
    rules_protoc_dependencies()

    rules_proto_dependencies()

    bazel_features_deps()

    protoc_toolchains(
        name = "toolchains_protoc_hub",
        version = "v25.3",
    )

    native.register_toolchains("//tools:all")
