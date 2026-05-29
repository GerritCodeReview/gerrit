load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "extensions_api_tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//java/com/google/gerrit/testing:gerrit-junit",
        "//lib:guava",
        "//lib/commons:text",
        "//lib/guice",
        "//lib/truth",
    ],
)
