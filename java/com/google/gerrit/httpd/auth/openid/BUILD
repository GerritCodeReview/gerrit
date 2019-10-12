load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "openid",
    srcs = glob(["**/*.java"]),
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
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib:jgit",
        "//lib/openid:consumer",
    ],
)
