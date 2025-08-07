load("@rules_java//java:defs.bzl", "java_library")

QUERY_BUILDER = ["QueryBuilder.java"]

java_library(
    name = "query_builder",
    srcs = QUERY_BUILDER,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//lib:guava",
        "//lib/errorprone:annotations",
        "//lib/lucene:lucene-core",
    ],
)

java_library(
    name = "lucene",
    srcs = glob(
        ["**/*.java"],
        exclude = QUERY_BUILDER,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":query_builder",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/entities/converter:converters",
        "//java/com/google/gerrit/entities/converter:proto_converter",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core",
        "//lib/lucene:lucene-misc",
    ],
)
