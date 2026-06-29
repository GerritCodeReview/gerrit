load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:flavour.bzl", "EE10_ONLY")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

# Flavour-aware: EE8/javax by default, EE10/jakarta under --@com_googlesource_gerrit_bazlets//flags:flavour=ee10.
alias(
    name = "oauth",
    actual = select({
        "//tools:ee10": ":oauth-ee10",
        "//conditions:default": ":oauth-ee8",
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "oauth-ee8",
    srcs = [":srcs"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/oauth"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/auth",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/server",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
    ],
)

# EE10 (jakarta.servlet) forward-generated flavour; purely mechanical.
javax_to_jakarta_srcjar(
    name = "oauth-ee10-srcs",
    direction = "to_jakarta",
    sources = [":srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "oauth-ee10",
    srcs = [":oauth-ee10-srcs"],
    target_compatible_with = EE10_ONLY,
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/oauth"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/auth",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/server",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jakarta-servlet-api",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
    ],
)
