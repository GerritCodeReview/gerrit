load("@com_googlesource_gerrit_bazlets//tools:servlet_flavour.bzl", "servlet_flavour_deps")
load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "servlet-ee8",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":nested",
        ":util-ajax",
        "@external_deps//:org_eclipse_jetty_ee8_jetty_ee8_servlet",
    ],
    runtime_deps = [
        ":security",
    ],
)

# Jetty jakarta servlet adapter for the ee11 flavour. The artifact is Jetty's
# EE11 environment: Servlet API 6.1 (shared with JGit) maps to EE11, the
# flavour name stays ee11. Pairs jetty-ee11-servlet with the
# jakarta servlet-api on the core :server chain. Twin of :servlet-ee8.
java_library(
    name = "servlet-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "//lib:servlet-api-ee11",
        "@external_deps//:org_eclipse_jetty_ee11_jetty_ee11_servlet",
    ],
    runtime_deps = [
        ":server",
    ],
)

# Flavour-neutral Jetty servlet adapter: EE8 (default) or EE11 servlet adapter
# based on --@com_googlesource_gerrit_bazlets//flags:flavour=ee11. Server bootstrap depends on this alias so one
# source set serves both release flavours.
servlet_flavour_deps(
    name = "servlet",
    license_deps = [
        ":servlet-ee11",
        ":servlet-ee8",
    ],
    part = "jetty",
    visibility = ["//visibility:public"],
)

java_library(
    name = "nested",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_eclipse_jetty_ee8_jetty_ee8_nested"],
)

java_library(
    name = "security",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@external_deps//:org_eclipse_jetty_ee8_jetty_ee8_security",
        "@external_deps//:org_eclipse_jetty_jetty_security",
    ],
    runtime_deps = [":server"],
)

java_library(
    name = "server",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":http",
        ":session",
        "@external_deps//:org_eclipse_jetty_jetty_server",
    ],
)

java_library(
    name = "jmx",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":http",
        "@external_deps//:org_eclipse_jetty_jetty_jmx",
    ],
)

java_library(
    name = "http",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":io",
        "@external_deps//:org_eclipse_jetty_jetty_http",
    ],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = [
        ":util",
        "@external_deps//:org_eclipse_jetty_jetty_io",
    ],
)

java_library(
    name = "util",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_eclipse_jetty_jetty_util"],
)

java_library(
    name = "session",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_eclipse_jetty_jetty_session"],
)

java_library(
    name = "util-ajax",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_eclipse_jetty_jetty_util_ajax"],
)
