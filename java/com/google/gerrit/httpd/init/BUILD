load("@com_googlesource_gerrit_bazlets//tools:servlet_transform.bzl", javax_to_jakarta_srcjar = "transform_srcjar")
load("@rules_java//java:defs.bzl", "java_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

_DEPS = [
    "//java/com/google/gerrit/auth",
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/gpg",
    "//java/com/google/gerrit/httpd",
    "//java/com/google/gerrit/httpd/auth/oauth",
    "//java/com/google/gerrit/httpd/auth/openid",
    "//java/com/google/gerrit/httpd/auth/restapi",
    "//java/com/google/gerrit/index",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/lucene",
    "//java/com/google/gerrit/metrics/dropwizard",
    "//java/com/google/gerrit/pgm/init",
    "//java/com/google/gerrit/pgm/util",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/server:module",
    "//java/com/google/gerrit/server/api",
    "//java/com/google/gerrit/server/audit",
    "//java/com/google/gerrit/server/cache/h2",
    "//java/com/google/gerrit/server/cache/mem",
    "//java/com/google/gerrit/server/restapi",
    "//java/com/google/gerrit/server/schema",
    "//java/com/google/gerrit/sshd",
    "//lib:guava",
    "//lib:jgit",
    "//lib:jgit-ssh-apache",
    "//lib/errorprone:annotations",
    "//lib/flogger:api",
    "//lib/guice",
    "//lib/guice:guice-servlet",
]

# Flavour-aware: EE8/javax by default, EE10/jakarta under --define=flavour=ee10.
alias(
    name = "init",
    actual = select({
        "//tools:ee10": ":init-ee10",
        "//conditions:default": ":init-ee8",
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "init-ee8",
    srcs = [":srcs"],
    visibility = ["//visibility:public"],
    deps = _DEPS + ["//lib:servlet-api"],
)

# EE10 (jakarta.servlet) forward-generated flavour; purely mechanical.
javax_to_jakarta_srcjar(
    name = "init-ee10-srcs",
    direction = "to_jakarta",
    sources = [":srcs"],
    src_prefix = "java/",
    visibility = ["//visibility:public"],
)

java_library(
    name = "init-ee10",
    srcs = [":init-ee10-srcs"],
    visibility = ["//visibility:public"],
    deps = _DEPS + ["//lib:jakarta-servlet-api"],
)
