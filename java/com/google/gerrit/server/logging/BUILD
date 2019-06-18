load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "logging",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server/util/time",
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
        "//lib/guice",
    ],
)
