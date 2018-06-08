package(default_visibility = ["//visibility:public"])

load("//tools/bzl:maven.bzl", "merge_maven_jars")

# core and backward-codecs both provide
# META-INF/services/org.apache.lucene.codecs.Codec, so they must be merged.
merge_maven_jars(
    name = "lucene-core-and-backward-codecs",
    srcs = [
        "@backward-codecs//jar",
        "@lucene-core//jar",
    ],
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "lucene-analyzers-common",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene-analyzers-common//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene-core//jar"],
)

java_library(
    name = "lucene-misc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene-misc//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-queryparser",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@lucene-queryparser//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)
