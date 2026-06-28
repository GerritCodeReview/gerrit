load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:flavour.bzl", "EE11_ONLY")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

# Flavour-aware: EE8/javax by default, EE11/jakarta under --define=flavour=ee11.
alias(
    name = "openid",
    actual = select({
        "//tools:ee11": ":openid-ee11",
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

# EE11 (jakarta.servlet) forward-generated flavour; purely mechanical.
javax_to_jakarta_srcjar(
    name = "openid-ee11-srcs",
    direction = "to_jakarta",
    sources = [":srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "openid-ee11",
    srcs = [":openid-ee11-srcs"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/openid"],
    target_compatible_with = EE11_ONLY,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/util/http:http-ee11",
        "//lib:guava",
        "//lib:jakarta-servlet-api",
        "//lib:jgit",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/openid:consumer",
    ],
)
