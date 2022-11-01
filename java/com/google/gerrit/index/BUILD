load("@rules_java//java:defs.bzl", "java_library")

QUERY_PARSE_EXCEPTION_SRCS = [
    "query/QueryParseException.java",
    "query/QueryRequiresAuthException.java",
]

INDEX_CONFIG_SRCS = [
    "IndexConfig.java",
    "IndexType.java",
    "PaginationType.java",
]

java_library(
    name = "query_exception",
    srcs = QUERY_PARSE_EXCEPTION_SRCS,
    visibility = ["//visibility:public"],
)

java_library(
    name = "index",
    srcs = glob(
        ["**/*.java"],
        exclude = QUERY_PARSE_EXCEPTION_SRCS + INDEX_CONFIG_SRCS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":query_exception",
        ":index_config",
        "//antlr3:query_parser",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/json",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server/logging",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/antlr:java-runtime",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
    ],
)

java_library(
    name = "index_config",
    srcs = INDEX_CONFIG_SRCS,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
