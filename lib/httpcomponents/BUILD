load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "fluent-hc",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@fluent-hc//jar"],
    runtime_deps = [":httpclient"],
)

java_library(
    name = "httpclient",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@httpclient//jar"],
    runtime_deps = [
        ":httpcore",
        "//lib/commons:codec",
        "//lib/log:jcl-over-slf4j",
    ],
)

java_library(
    name = "httpcore",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@httpcore//jar"],
)
