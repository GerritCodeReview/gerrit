load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

flavoured_library(
    name = "openid",
    srcs = [":srcs"],
    canonical = "ee11",
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/openid"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/util/http",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/openid:consumer",
    ],
)
