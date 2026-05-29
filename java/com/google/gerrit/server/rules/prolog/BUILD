load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "prolog",
    srcs = glob(
        ["*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-value-annotations",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/prolog:runtime",
    ],
)
