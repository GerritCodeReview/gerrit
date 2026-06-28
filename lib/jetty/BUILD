load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":util-ajax",
        "@external_deps//:org_eclipse_jetty_ee8_jetty_ee8_servlet",
    ],
    runtime_deps = [
        ":nested",
        ":security",
    ],
)

# Jakarta servlet adapter for the EE11 release flavour. The artifact is
# Jetty's EE11 environment: Servlet API 6.1 (shared with JGit) maps to EE11;
# the flavour name stays ee11. Mirrors gitiles' lib/jetty:servlet-ee11.
# Artifact comes from the shared external_deps (contributed by jgit master).
# Pairs jetty-ee11-servlet with the jakarta servlet-api on the core :server
# chain. Runtime deps are intentionally
# minimal for now (no ee8 :nested / ee8 :security); the full EE11 server wiring
# is added when httpd-ee11 lands.
java_library(
    name = "servlet-ee11",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "//lib:jakarta-servlet-api",
        "@external_deps//:org_eclipse_jetty_ee11_jetty_ee11_servlet",
    ],
    runtime_deps = [
        ":server",
    ],
)

# Flavour-neutral Jetty servlet adapter: EE8 (default) or EE11 servlet adapter
# based on --@com_googlesource_gerrit_bazlets//flags:flavour=ee11. Server bootstrap depends on this alias so one
# source set serves both release flavours.
alias(
    name = "servlet-flavoured",
    actual = select({
        "//tools:ee11": ":servlet-ee11",
        "//conditions:default": ":servlet",
    }),
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
