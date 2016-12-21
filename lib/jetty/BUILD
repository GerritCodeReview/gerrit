java_library(
    name = "servlet",
    visibility = ["//visibility:public"],
    exports = ["@jetty_servlet//jar"],
    runtime_deps = [":security"],
)

java_library(
    name = "security",
    visibility = ["//visibility:public"],
    exports = ["@jetty_security//jar"],
    runtime_deps = [":server"],
)

java_library(
    name = "servlets",
    visibility = ["//visibility:public"],
    exports = ["@jetty_servlets//jar"],
)

java_library(
    name = "server",
    visibility = ["//visibility:public"],
    exports = [
        ":continuation",
        ":http",
        "@jetty_server//jar",
    ],
)

java_library(
    name = "jmx",
    visibility = ["//visibility:public"],
    exports = [
        ":continuation",
        ":http",
        "@jetty_jmx//jar",
    ],
)

java_library(
    name = "continuation",
    visibility = ["//visibility:public"],
    exports = ["@jetty_continuation//jar"],
)

java_library(
    name = "http",
    visibility = ["//visibility:public"],
    exports = [
        ":io",
        "@jetty_http//jar",
    ],
)

java_library(
    name = "io",
    exports = [
        ":util",
        "@jetty_io//jar",
    ],
)

java_library(
    name = "util",
    exports = ["@jetty_util//jar"],
)
