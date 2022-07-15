load(
    "@bazel_tools//tools/jdk:default_java_toolchain.bzl",
    "default_java_toolchain",
)
load("@rules_java//java:defs.bzl", "java_package_configuration")

exports_files(["nongoogle.bzl"])

default_java_toolchain(
    name = "error_prone_warnings_toolchain_java11",
    package_configuration = [
        ":error_prone",
    ],
    source_version = "11",
    target_version = "11",
    visibility = ["//visibility:public"],
)

default_java_toolchain(
    name = "error_prone_warnings_toolchain_java17",
    configuration = dict(),
    java_runtime = "@bazel_tools//tools/jdk:remotejdk_17",
    package_configuration = [
        ":error_prone",
    ],
    source_version = "17",
    target_version = "17",
    visibility = ["//visibility:public"],
)

# Error Prone errors enabled by default; see ../.bazelrc for how this is
# enabled. This warnings list is originally based on:
# https://github.com/bazelbuild/BUILD_file_generator/blob/master/tools/bazel_defs/java.bzl
# Additionally, items used internally in google is added. Such items have
# the same or higher verbosity level than in google.
# However, feel free to add any additional errors. Thus far they have all been pretty useful.
# All warnings are commented to avoid noise in the output.
# Newer versions of error-prone have XepDisableAllWarnings flag which could
# be used instead of commenting. Bazel should be updated to use a new version
# of error-prone.
java_package_configuration(
    name = "error_prone",
    javacopts = [
    ],
    packages = ["error_prone_packages"],
)

package_group(
    name = "error_prone_packages",
    packages = [
        "//java/...",
        "//javatests/...",
        "//plugins/codemirror-editor/...",
        "//plugins/commit-message-length-validator/...",
        "//plugins/delete-project/...",
        "//plugins/download-commands/...",
        "//plugins/gitiles/...",
        "//plugins/hooks/...",
        "//plugins/plugin-manager/...",
        "//plugins/replication/...",
        "//plugins/reviewnotes/...",
        "//plugins/singleusergroup/...",
        "//plugins/webhooks/...",
    ],
)
