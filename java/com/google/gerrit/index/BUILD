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
    name = "index",
    srcs = glob(
        ["**/*.java"],
        exclude = QUERY_PARSE_EXCEPTION_SRCS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":query_exception",
        "//antlr3:query_parser",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/json",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gwtorm",
        "//lib:guava",
        "//lib/antlr:java-runtime",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
