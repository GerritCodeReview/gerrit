load("@rules_python//python:defs.bzl", "py_binary", "py_test")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

exports_files([
    "diff_allowlist.sh",
    "license-map.py",
    "test_empty.sh",
    "test_license.sh",
    "war_checks.bzl",
])

# Verifies every artifact pinned in external_deps.lock.json is present on the
# gerrit-maven mirror. Run before the build so a non-mirrored dependency fails
# fast, locally and in CI, without hitting Maven Central:
#   bazel run //tools/bzl:gerrit-maven-mirror-check
py_binary(
    name = "gerrit-maven-mirror-check",
    srcs = ["gerrit_maven_mirror_check.py"],
    main = "gerrit_maven_mirror_check.py",
    visibility = ["//visibility:public"],
)

py_test(
    name = "gerrit_maven_mirror_check_test",
    size = "small",
    srcs = [
        "gerrit_maven_mirror_check.py",
        "gerrit_maven_mirror_check_test.py",
    ],
    imports = ["."],
    main = "gerrit_maven_mirror_check_test.py",
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
