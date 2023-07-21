load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "field_tests",
    size = "small",
    srcs = glob(["*.java"]),
    tags = ["metrics"],
    deps = [
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/truth",
    ],
)
