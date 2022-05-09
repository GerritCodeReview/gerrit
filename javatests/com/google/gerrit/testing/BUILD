load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "testing_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:jgit",
        "//lib/truth",
    ],
)
