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
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "lucene-analyzers-common",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene_analyzers_common//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene_core//jar"],
)

java_library(
    name = "lucene-misc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene_misc//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-queryparser",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene_queryparser//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-highlighter",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene_highlighter//jar"],
)

java_library(
    name = "lucene-join",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene_join//jar"],
)

java_library(
    name = "lucene-memory",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene_memory//jar"],
)

java_library(
    name = "lucene-spatial",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene_spatial//jar"],
)

java_library(
    name = "lucene-suggest",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene_suggest//jar"],
)

java_library(
    name = "lucene-queries",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene_queries//jar"],
)
