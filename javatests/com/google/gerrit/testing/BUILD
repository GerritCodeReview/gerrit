load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "testing_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//gerrit-server:server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:truth",
    ],
)
