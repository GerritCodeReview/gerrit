load("//tools/bzl:genrule2.bzl", "genrule2")

java_library(
    name = "query_exception",
    srcs = [
        "src/main/java/com/google/gerrit/server/query/QueryParseException.java",
        "src/main/java/com/google/gerrit/server/query/QueryRequiresAuthException.java",
    ],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "query_antlr",
    srcs = ["src/main/antlr3/com/google/gerrit/server/query/Query.g"],
    outs = ["query_antlr.srcjar"],
    cmd = " && ".join([
        "$(location //lib/antlr:antlr-tool) -o $$TMP $<",
        "cd $$TMP",
        "zip -q $$ROOT/$@ $$(find . -type f )",
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
        "//lib/antlr:java-runtime",
    ],
)
