load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "proto",
    srcs = ["Protos.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:protobuf",
        "//lib/errorprone:annotations",
    ],
)
