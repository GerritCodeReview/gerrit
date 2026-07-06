load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_java_library", "flavoured_twin_alias")
load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

JETTY_DEPS = [
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/httpd",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/metrics",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/server/util/time",
    "//java/com/google/gerrit/sshd",
    "//java/com/google/gerrit/util/http",
    "//java/com/google/gerrit/util/logging",
    "//lib:gson",
    "//lib:guava",
    "//lib:jgit",
    "//lib:servlet-api",
    "//lib/errorprone:annotations",
    "//lib/flogger:api",
    "//lib/guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/jetty:jmx",
    "//lib/jetty:server",
    "//lib/jetty:servlet",
    "//lib/log:log4j",
]

flavoured_twin_alias(
    name = "jetty",
    flavours = ["ee11"],
    visibility = ["//visibility:public"],
)

flavoured_java_library(
    name = "jetty-ee8",
    srcs = [":srcs"],
    flavour = "ee8",
    visibility = ["//visibility:public"],
    deps = JETTY_DEPS,
)

javax_to_jakarta_srcjar(
    name = "jetty-ee11-srcs",
    direction = "to_jakarta",
    # Canonical sources MINUS the two files with genuine Jetty-12 ee11 API
    # divergences, which the hand-written ee11/ overlays replace.
    sources = glob(
        ["**/*.java"],
        exclude = [
            "HiddenErrorHandler.java",
            "JettyServerFlavour.java",
        ],
    ),
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

flavoured_java_library(
    name = "jetty-ee11",
    srcs = [
        ":jetty-ee11-srcs",
        "//java/com/google/gerrit/pgm/http/jetty/ee11:overlay",
    ],
    flavour = "ee11",
    visibility = ["//visibility:public"],
    deps = JETTY_DEPS,
)
