load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "logging",
    srcs = glob(
        ["*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:gson",
        "//lib/log:log4j",
    ],
)
