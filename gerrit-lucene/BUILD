QUERY_BUILDER = [
    "src/main/java/com/google/gerrit/lucene/QueryBuilder.java",
]

java_library(
    name = "query_builder",
    srcs = QUERY_BUILDER,
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-antlr:query_exception",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib/lucene:lucene-core-and-backward-codecs",
    ],
)

java_library(
    name = "lucene",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = QUERY_BUILDER,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":query_builder",
        "//gerrit-antlr:query_exception",
        "//gerrit-common:annotations",
        "//gerrit-common:server",
        "//gerrit-extension-api:api",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core-and-backward-codecs",
        "//lib/lucene:lucene-misc",
    ],
)
