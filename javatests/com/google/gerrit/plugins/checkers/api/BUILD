load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "api_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/plugins/checkers:checkers__plugin",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/truth",
    ],
)
