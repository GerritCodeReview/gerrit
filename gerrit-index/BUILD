load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:junit.bzl", "junit_tests")

QUERY_PARSE_EXCEPTION_SRCS = ["src/main/java/com/google/gerrit/index/query/QueryParseException.java"]

java_library(
    name = "query_exception",
    srcs = QUERY_PARSE_EXCEPTION_SRCS,
    visibility = ["//visibility:public"],
)

java_library(
    name = "query_parser",
    srcs = ["//antlr3:query"],
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
        "//gerrit-server:metrics",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib/antlr:java_runtime",
        "//lib/auto:auto-value",
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
        "//lib/antlr:java_runtime",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
