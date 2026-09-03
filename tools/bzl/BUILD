load("@rules_python//python:defs.bzl", "py_binary", "py_test")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

exports_files([
    "diff_allowlist.sh",
    "license-map.py",
    "test_empty.sh",
    "test_license.sh",
    "war_checks.bzl",
])

# Manage the gerrit-maven mirror of the RJE-locked Maven Central artifacts:
# checks coverage by default; --upload populates the missing ones. The check
# runs before the build so a non-mirrored dependency fails fast, locally and
# in CI, without hitting Maven Central:
#   bazel run //tools/bzl:maven-mirror              # check (default)
#   bazel run //tools/bzl:maven-mirror -- --upload  # populate
py_binary(
    name = "maven-mirror",
    srcs = ["manage_maven_central_mirror.py"],
    main = "manage_maven_central_mirror.py",
    visibility = ["//visibility:public"],
)

py_test(
    name = "manage_maven_central_mirror_test",
    size = "small",
    srcs = [
        "manage_maven_central_mirror.py",
        "manage_maven_central_mirror_test.py",
    ],
    imports = ["."],
    main = "manage_maven_central_mirror_test.py",
)

sh_test(
    name = "always_pass_test",
    srcs = ["always_pass_test.sh"],
    tags = ["no_rbe"],
)

# Mirror the PolyGerrit github npm tarballs to gerrit-maven:
#   bazel run //tools/bzl:npm-mirror                       # verify (default)
#   bazel run //tools/bzl:npm-mirror -- --manifest-update
#   bazel run //tools/bzl:npm-mirror -- --upload
py_binary(
    name = "npm-mirror",
    srcs = ["mirror_github_npm_deps.py"],
    main = "mirror_github_npm_deps.py",
    visibility = ["//visibility:public"],
)

# Mirror the bazel_lib toolchain binaries to gerrit-maven:
#   bazel run //tools/bzl:bazel-lib-mirror                 # verify (default)
#   bazel run //tools/bzl:bazel-lib-mirror -- --manifest-update
#   bazel run //tools/bzl:bazel-lib-mirror -- --upload
py_binary(
    name = "bazel-lib-mirror",
    srcs = ["mirror_bazel_lib_toolchains.py"],
    main = "mirror_bazel_lib_toolchains.py",
    visibility = ["//visibility:public"],
)

# Mirror the protobuf prebuilt protoc binaries to gerrit-maven:
#   bazel run //tools/bzl:protobuf-mirror                  # verify (default)
#   bazel run //tools/bzl:protobuf-mirror -- --manifest-update
#   bazel run //tools/bzl:protobuf-mirror -- --upload
py_binary(
    name = "protobuf-mirror",
    srcs = ["mirror_protobuf_toolchain.py"],
    main = "mirror_protobuf_toolchain.py",
    visibility = ["//visibility:public"],
)
