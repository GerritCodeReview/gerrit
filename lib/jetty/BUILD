java_library(
    name = "servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jetty_servlet//jar"],
    runtime_deps = [":security"],
)

java_library(
    name = "security",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jetty_security//jar"],
    runtime_deps = [":server"],
)

java_library(
    name = "servlets",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jetty_servlets//jar"],
)

java_library(
    name = "server",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":continuation",
        ":http",
        "@jetty_server//jar",
    ],
)

java_library(
    name = "jmx",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":continuation",
        ":http",
        "@jetty_jmx//jar",
    ],
)

java_library(
    name = "continuation",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jetty_continuation//jar"],
)

java_library(
    name = "http",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":io",
        "@jetty_http//jar",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = [
        ":util",
        "@jetty_io//jar",
    ],
)

java_library(
    name = "util",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jetty_util//jar"],
)
