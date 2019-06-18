load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//antlr3:query_parser",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/index/query/testing",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:junit",
        "//lib/antlr:java-runtime",
        "//lib/truth",
    ],
)
