load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "server_tests",
    srcs = glob(["*.java"]),
    tags = ["no_windows"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/common:version",
        "//java/com/google/gerrit/launcher",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/truth",
    ],
)
