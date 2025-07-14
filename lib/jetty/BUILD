load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":util-ajax",
        "@gerrit_deps//:org_eclipse_jetty_jetty_servlet",
    ],
    runtime_deps = [":security"],
)

java_library(
    name = "security",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@gerrit_deps//:org_eclipse_jetty_jetty_security"],
    runtime_deps = [":server"],
)

java_library(
    name = "server",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":http",
        "@gerrit_deps//:org_eclipse_jetty_jetty_server",
    ],
)

java_library(
    name = "jmx",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":http",
        "@gerrit_deps//:org_eclipse_jetty_jetty_jmx",
    ],
)

java_library(
    name = "http",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":io",
        "@gerrit_deps//:org_eclipse_jetty_jetty_http",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = [
        ":util",
        "@gerrit_deps//:org_eclipse_jetty_jetty_io",
    ],
)

java_library(
    name = "util",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@gerrit_deps//:org_eclipse_jetty_jetty_util"],
)

java_library(
    name = "util-ajax",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@gerrit_deps//:org_eclipse_jetty_jetty_util_ajax"],
)
