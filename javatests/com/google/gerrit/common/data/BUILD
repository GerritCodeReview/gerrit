load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "data_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/truth",
    ],
)
