load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":util-ajax",
        "@maven_deps//jetty-servlet/jar",
    ],
    runtime_deps = [":security"],
)

java_library(
    name = "security",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//jetty-security/jar"],
    runtime_deps = [":server"],
)

java_library(
    name = "server",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":http",
        "@maven_deps//jetty-server/jar",
    ],
)

java_library(
    name = "jmx",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":http",
        "@maven_deps//jetty-jmx/jar",
    ],
)

java_library(
    name = "http",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":io",
        "@maven_deps//jetty-http/jar",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = [
        ":util",
        "@maven_deps//jetty-io/jar",
    ],
)

java_library(
    name = "util",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//jetty-util/jar"],
)

java_library(
    name = "util-ajax",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//jetty-util-ajax/jar"],
)
