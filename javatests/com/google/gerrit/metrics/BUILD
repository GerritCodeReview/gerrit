load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "field_tests",
    size = "small",
    tags = ["metrics"],
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/truth",
    ],
)
