load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "prettify_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/prettify:server",
        "//java/com/google/gerrit/prettify/common/testing",
        "//java/com/google/gerrit/testing:gerrit-junit",
        "//lib:guava",
        "//lib/truth",
    ],
)
