load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "testing_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/git/testing",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/truth",
    ],
)
