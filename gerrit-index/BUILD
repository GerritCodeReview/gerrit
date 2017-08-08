load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:junit.bzl", "junit_tests")

QUERY_PARSE_EXCEPTION_SRCS = ["src/main/java/com/google/gerrit/index/query/QueryParseException.java"]

java_library(
    name = "query_exception",
    srcs = QUERY_PARSE_EXCEPTION_SRCS,
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
    visibility = ["//gerrit-plugin-api:__pkg__"],
    deps = [
        ":query_exception",
        "//lib/antlr:java_runtime",
    ],
)

java_library(
    name = "index",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = QUERY_PARSE_EXCEPTION_SRCS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":query_exception",
        ":query_parser",
        "//gerrit-common:annotations",
        "//gerrit-extension-api:api",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib/antlr:java_runtime",
        "//lib/auto:auto-value",
        "//lib/guice",  # TODO(dborowitz): Replace Provider with Supplier.
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)

junit_tests(
    name = "index_tests",
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":index",
        ":query_exception",
        ":query_parser",
        "//gerrit-server:testutil",
        "//lib:junit",
        "//lib:truth",
        "//lib/antlr:java_runtime",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
