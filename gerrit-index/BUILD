load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "query_exception",
    srcs = ["src/main/java/com/google/gerrit/index/query/QueryParseException.java"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "query_antlr",
    srcs = ["src/main/antlr3/com/google/gerrit/index/query/Query.g"],
    outs = ["query_antlr.srcjar"],
    cmd = " && ".join([
        "$(location //lib/antlr:antlr-tool) -o $$TMP $<",
        "cd $$TMP",
        "$$ROOT/$(location @bazel_tools//tools/zip:zipper) cC $$ROOT/$@ $$(find *)",
    ]),
    tools = [
        "//lib/antlr:antlr-tool",
        "@bazel_tools//tools/zip:zipper",
    ],
)

java_library(
    name = "query_parser",
    srcs = [":query_antlr"],
    visibility = ["//visibility:public"],
    deps = [
        ":query_exception",
        "//lib/antlr:java_runtime",
    ],
)

junit_tests(
    name = "index_tests",
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":query_exception",
        ":query_parser",
        "//lib/antlr:java_runtime",
    ],
)
