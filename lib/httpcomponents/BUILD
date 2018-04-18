package(default_visibility = ["//visibility:public"])

java_library(
    name = "fluent-hc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@fluent_hc//jar"],
    runtime_deps = [":httpclient"],
)

java_library(
    name = "httpclient",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
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
    visibility = ["//visibility:public"],
    exports = ["@httpcore//jar"],
)

java_library(
    name = "httpmime",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@httpmime//jar"],
)

java_library(
    name = "httpasyncclient",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@httpasyncclient//jar"],
)

java_library(
    name = "httpcore-nio",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@httpcore_nio//jar"],
)
