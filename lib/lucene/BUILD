package(default_visibility = ["//visibility:public"])

load("//tools/bzl:maven.bzl", "merge_maven_jars")

# core and backward-codecs both provide
# META-INF/services/org.apache.lucene.codecs.Codec, so they must be merged.
merge_maven_jars(
    name = "lucene-core-and-backward-codecs",
    srcs = [
        "@backward_codecs//jar",
        "@lucene_core//jar",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "lucene-analyzers-common",
    visibility = ["//visibility:public"],
    exports = ["@lucene_analyzers_common//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-codecs",
    visibility = ["//visibility:public"],
    exports = ["@lucene_codecs//jar"],
)

java_library(
    name = "lucene-core",
    visibility = ["//visibility:public"],
    exports = ["@lucene_core//jar"],
)

java_library(
    name = "lucene-misc",
    visibility = ["//visibility:public"],
    exports = ["@lucene_misc//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-queryparser",
    visibility = ["//visibility:public"],
    exports = ["@lucene_queryparser//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-highlighter",
    exports = ["@lucene_highlighter//jar"],
)

java_library(
    name = "lucene-join",
    exports = ["@lucene_join//jar"],
)

java_library(
    name = "lucene-memory",
    exports = ["@lucene_memory//jar"],
)

java_library(
    name = "lucene-sandbox",
    exports = ["@lucene_sandbox//jar"],
)

java_library(
    name = "lucene-spatial",
    exports = ["@lucene_spatial//jar"],
)

java_library(
    name = "lucene-suggest",
    exports = ["@lucene_suggest//jar"],
)

java_library(
    name = "lucene-queries",
    exports = ["@lucene_queries//jar"],
)
