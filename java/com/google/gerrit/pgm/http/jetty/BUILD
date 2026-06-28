load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:flavour.bzl", "EE11_ONLY")

# Canonical EE8 sources, shared by the EE8 :jetty library and the EE11 transform.
filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

# Flavour-aware jetty bootstrap: dependents use
# //java/com/google/gerrit/pgm/http/jetty:jetty and get EE8 by default or the
# EE11 library under --@com_googlesource_gerrit_bazlets//flags:flavour=ee11, so the release WAR flips flavour.
alias(
    name = "jetty",
    actual = select({
        "//tools:ee11": ":jetty-ee11",
        "//conditions:default": ":jetty-ee8",
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "jetty-ee8",
    srcs = [":srcs"],
    visibility = ["//visibility:public"],
    deps = [
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
        "//lib/jetty:nested",
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/log:log4j",
    ],
)

# --- EE11 (jakarta.servlet) forward-generated jetty bootstrap ---------------
# Most files transform mechanically (gerrit is already on Jetty 12; only the
# ee8 -> ee11 adapter prefix changes). The genuinely divergent files (Jetty-12
# ee11 core-API: HiddenErrorHandler.handle, JettyServer config) get hand-written
# overlays under ee11/ and are excluded from the transform. Built under
# --@com_googlesource_gerrit_bazlets//flags:flavour=ee11 (the Guice tier flips to Guice 7 via the cascade).
# Transform input: canonical sources MINUS the two files with genuine Jetty-12
# ee11 API divergences, which are replaced by the hand-written ee11/ overlays.
filegroup(
    name = "transformable-srcs",
    srcs = glob(
        ["**/*.java"],
        exclude = [
            "HiddenErrorHandler.java",
            "JettyServerFlavour.java",
        ],
    ),
    visibility = ["//visibility:public"],
)

javax_to_jakarta_srcjar(
    name = "jetty-ee11-srcs",
    direction = "to_jakarta",
    sources = [":transformable-srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "jetty-ee11",
    srcs = [
        ":jetty-ee11-srcs",
        "//java/com/google/gerrit/pgm/http/jetty/ee11:overlay",
    ],
    target_compatible_with = EE11_ONLY,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/sshd",
        "//java/com/google/gerrit/util/http:http-ee11",
        "//java/com/google/gerrit/util/logging",
        "//lib:gson",
        "//lib:guava",
        "//lib:jakarta-servlet-api",
        "//lib:jgit",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jetty:jmx",
        "//lib/jetty:server",
        "//lib/jetty:servlet-ee11",
        "//lib/log:log4j",
    ],
)
