load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "lucene-analyzers-common",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-analyzers-common//jar"],
    runtime_deps = [":lucene-core"],
)

java_library(
    name = "lucene-backward-codecs",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-backward-codecs//jar"],
)

java_library(
    name = "lucene-core",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-core//jar"],
    runtime_deps = [":lucene-backward-codecs"],
)

java_library(
    name = "lucene-misc",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-misc//jar"],
    runtime_deps = [":lucene-core"],
)

java_library(
    name = "lucene-queryparser",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@lucene-queryparser//jar"],
    runtime_deps = [":lucene-core"],
)
