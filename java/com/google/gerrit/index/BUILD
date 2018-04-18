load("//tools/bzl:genrule2.bzl", "genrule2")

QUERY_PARSE_EXCEPTION_SRCS = [
    "query/QueryParseException.java",
    "query/QueryRequiresAuthException.java",
]

java_library(
    name = "query_exception",
    srcs = QUERY_PARSE_EXCEPTION_SRCS,
    visibility = ["//visibility:public"],
)

java_library(
    name = "query_parser",
    srcs = ["//antlr3:query"],
    visibility = [
        "//javatests/com/google/gerrit/index:__pkg__",
        "//plugins:__pkg__",
    ],
    deps = [
        ":query_exception",
        "//lib/antlr:java_runtime",
    ],
)

java_library(
    name = "index",
    srcs = glob(
        ["**/*.java"],
        exclude = QUERY_PARSE_EXCEPTION_SRCS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":query_exception",
        ":query_parser",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/metrics",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib/antlr:java_runtime",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)
