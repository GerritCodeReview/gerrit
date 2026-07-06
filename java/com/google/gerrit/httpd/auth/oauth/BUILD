load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

flavoured_library(
    name = "oauth",
    srcs = [":srcs"],
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/httpd/auth/oauth"],
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
