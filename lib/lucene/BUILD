load("@rules_java//java:defs.bzl", "java_binary", "java_import", "java_library")

package(default_visibility = ["//visibility:public"])

# Merge jars so
# META-INF/services/org.apache.lucene.codecs.Codec
# contains the union of both Codec collections.
java_binary(
    name = "lucene-core-and-backward-codecs-merged",
    data = ["//lib:LICENSE-Apache2.0"],
    main_class = "NotImportant",
    runtime_deps = [
        # in case of conflict, we want the implementation of backwards-codecs
        # first.
        "@backward-codecs//jar",
        "@lucene-core//jar",
    ],
)

java_import(
    name = "lucene-core-and-backward-codecs",
    jars = [
        ":lucene-core-and-backward-codecs-merged_deploy.jar",
    ],
)

java_library(
    name = "lucene-analyzers-common",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-analyzers-common//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-core",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-core//jar"],
)

java_library(
    name = "lucene-misc",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-misc//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)

java_library(
    name = "lucene-queryparser",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-queryparser//jar"],
    runtime_deps = [":lucene-core-and-backward-codecs"],
)
