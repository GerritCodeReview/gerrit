load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "extensions_api_tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//lib/guice",
        "//lib/truth",
    ],
)
