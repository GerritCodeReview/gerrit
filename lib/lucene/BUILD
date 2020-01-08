load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:maven.bzl", "merge_maven_jars")

package(default_visibility = ["//visibility:public"])

# merge jars so there is only one
# META-INF/services/org.apache.lucene.codecs.Codec
java_library(
    name = "lucene-core-and-backward-codecs",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = [
        # Backward codecs should come before lucene-core. We want
        # backward-codecs to take precedence so schema upgrades can read data
        # from older Lucene versions.
        "@backward-codecs//jar",

        # The comments here and above is an extra safeguard to ensure that
        # buildifier will not reorder these entries.
        "@lucene-core//jar",
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
