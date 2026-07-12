load("@rules_java//java:defs.bzl", "java_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

java_library(
    name = "http",
    srcs = [":srcs"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
    ],
)
