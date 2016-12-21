package(default_visibility = ["//visibility:public"])

java_library(
    name = "fluent-hc",
    visibility = ["//visibility:public"],
    exports = ["@fluent_hc//jar"],
    runtime_deps = [":httpclient"],
)

java_library(
    name = "httpclient",
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
    visibility = ["//visibility:public"],
    exports = ["@httpcore//jar"],
)

java_library(
    name = "httpmime",
    visibility = ["//visibility:public"],
    exports = ["@httpmime//jar"],
)

java_library(
    name = "httpasyncclient",
    exports = ["@httpasyncclient//jar"],
)

java_library(
    name = "httpcore-nio",
    exports = ["@httpcore_nio//jar"],
)

java_library(
    name = "httpcore-niossl",
    exports = ["@httpcore_niossl//jar"],
)
