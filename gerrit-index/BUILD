load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:junit.bzl", "junit_tests")

QUERY_PARSE_EXCEPTION_SRCS = [
    "src/main/java/com/google/gerrit/index/query/QueryParseException.java",
    "src/main/java/com/google/gerrit/index/query/QueryRequiresAuthException.java",
]

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
    visibility = ["//gerrit-plugin-api:__pkg__"],
    deps = [
        ":query_exception",
        "//lib/antlr:java-runtime",
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
        "//gerrit-server:metrics",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib/antlr:java-runtime",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
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
        "//lib:junit",
        "//lib:truth",
        "//lib/antlr:java-runtime",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
