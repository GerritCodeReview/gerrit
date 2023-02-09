load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "update_context_tests",
    size = "small",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/testing:test-ref-update-context",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
