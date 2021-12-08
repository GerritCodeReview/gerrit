load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//antlr3:query_parser",
        "//java/com/google/gerrit/lucene",
        "//lib:guava",
        "//lib:jgit",
        "//lib:junit",
        "//lib/antlr:java-runtime",
        "//lib/truth",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//lib/lucene:lucene-core-and-backward-codecs"
    ],
)
