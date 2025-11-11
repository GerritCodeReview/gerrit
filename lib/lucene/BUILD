load("@rules_java//java:defs.bzl", "java_binary", "java_import", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "lucene-analyzers-common",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//lucene-analyzers-common/jar"],
    runtime_deps = [":lucene-core"],
)

java_library(
    name = "lucene-backward-codecs",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//lucene-backward-codecs/jar"],
)

java_library(
    name = "lucene-core",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//lucene-core/jar"],
    runtime_deps = [":lucene-backward-codecs"],
)

java_library(
    name = "lucene-misc",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//lucene-misc/jar"],
    runtime_deps = [":lucene-core"],
)

java_library(
    name = "lucene-queryparser",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//lucene-queryparser/jar"],
    runtime_deps = [":lucene-core"],
)
