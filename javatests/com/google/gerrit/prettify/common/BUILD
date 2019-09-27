load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "common_tests",
    srcs = glob(["**/*.java"]),
    tags = ["prettify"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/prettify:server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/truth",
    ],
)
