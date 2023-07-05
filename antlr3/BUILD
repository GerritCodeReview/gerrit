load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:genrule2.bzl", "genrule2")

genrule2(
    name = "query",
    srcs = ["com/google/gerrit/index/query/Query.g"],
        "zip -q $$ROOT/$@ $$(find . -type f)",
    ]),
    tools = [
        "//lib/antlr:antlr-tool",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "query_parser",
    srcs = [":query"],
    visibility = [
        "//java/com/google/gerrit/index:__subpackages__",
        "//java/com/google/gerrit/server:__subpackages__",
        "//javatests/com/google/gerrit:__subpackages__",
        "//javatests/com/google/gerrit/index:__pkg__",
        "//plugins:__pkg__",
    ],
    deps = [
        "//java/com/google/gerrit/index:query_exception",
        "//lib/antlr:java-runtime",
    ],
)
