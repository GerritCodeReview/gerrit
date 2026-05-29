load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "server",
    srcs = glob(["common/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/errorprone:annotations",
    ],
)
