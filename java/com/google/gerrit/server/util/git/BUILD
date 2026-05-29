load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "git",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//lib:jgit",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
    ],
)
