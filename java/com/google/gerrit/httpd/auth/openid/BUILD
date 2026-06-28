load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

# Flavour-aware: EE8/javax by default, EE10/jakarta under --define=flavour=ee10.
alias(
    name = "openid",
    actual = select({
        "//tools:ee10": ":openid-ee10",
        "//conditions:default": ":openid-ee8",
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "openid-ee8",
    srcs = [":srcs"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/openid"],
    visibility = ["//visibility:public"],
    deps = [
        # We want all these deps to be provided_deps
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/util/http",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib:jgit",
        "//lib/openid:consumer",
    ],
)

# EE10 (jakarta.servlet) forward-generated flavour; purely mechanical.
javax_to_jakarta_srcjar(
    name = "openid-ee10-srcs",
    direction = "to_jakarta",
    sources = [":srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "openid-ee10",
    srcs = [":openid-ee10-srcs"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/openid"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/util/http:http-ee10",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:jakarta-servlet-api",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib:jgit",
        "//lib/openid:consumer",
    ],
)
