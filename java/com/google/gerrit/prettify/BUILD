load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "server",
    srcs = glob(["common/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:jgit",
    ],
)
