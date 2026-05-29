load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "lucene-analyzers-common",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_lucene_lucene_analysis_common"],
    runtime_deps = [":lucene-core"],
)

java_library(
    name = "lucene-backward-codecs",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_lucene_lucene_backward_codecs"],
)

java_library(
    name = "lucene-core",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_lucene_lucene_core"],
    runtime_deps = [":lucene-backward-codecs"],
)

java_library(
    name = "lucene-misc",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_lucene_lucene_misc"],
    runtime_deps = [":lucene-core"],
)

java_library(
    name = "lucene-queryparser",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_lucene_lucene_queryparser"],
    runtime_deps = [":lucene-core"],
)
