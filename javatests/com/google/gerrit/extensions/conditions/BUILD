load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "conditions_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/extensions:lib",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/truth",
    ],
)
