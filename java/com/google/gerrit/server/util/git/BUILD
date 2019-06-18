load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "git",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:jgit",
    ],
)
