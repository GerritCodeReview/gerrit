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
