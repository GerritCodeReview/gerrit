load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:flavour.bzl", "EE10_ONLY")

# Canonical EE8 sources, shared by the EE8 :jetty library and the EE10 transform.
filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

# Flavour-aware jetty bootstrap: dependents use
# //java/com/google/gerrit/pgm/http/jetty:jetty and get EE8 by default or the
# EE10 library under --@com_googlesource_gerrit_bazlets//flags:flavour=ee10, so the release WAR flips flavour.
alias(
    name = "jetty",
    actual = select({
        "//tools:ee10": ":jetty-ee10",
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

# --- EE10 (jakarta.servlet) forward-generated jetty bootstrap ---------------
# Most files transform mechanically (gerrit is already on Jetty 12; only the
# ee8 -> ee10 adapter prefix changes). The genuinely divergent files (Jetty-12
# ee10 core-API: HiddenErrorHandler.handle, JettyServer config) get hand-written
# overlays under ee10/ and are excluded from the transform. Built under
# --@com_googlesource_gerrit_bazlets//flags:flavour=ee10 (the Guice tier flips to Guice 7 via the cascade).
# Transform input: canonical sources MINUS the two files with genuine Jetty-12
# ee10 API divergences, which are replaced by the hand-written ee10/ overlays.
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
    name = "jetty-ee10-srcs",
    direction = "to_jakarta",
    sources = [":transformable-srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "jetty-ee10",
    target_compatible_with = EE10_ONLY,
    srcs = [
        ":jetty-ee10-srcs",
        "//java/com/google/gerrit/pgm/http/jetty/ee10:overlay",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/sshd",
        "//java/com/google/gerrit/util/http:http-ee10",
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
        "//lib/jetty:servlet-ee10",
        "//lib/log:log4j",
    ],
)
