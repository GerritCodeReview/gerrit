package(default_visibility = ["//java/com/google/gerrit/plugins/checkers:visibility"])

load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "checkers_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/plugins/checkers:checkers__plugin",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/truth",
    ],
)
