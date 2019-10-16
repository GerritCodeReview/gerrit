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
        "//lib/lucene:lucene-core-and-backward-codecs",
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
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core-and-backward-codecs",
        "//lib/lucene:lucene-misc",
    ],
)
