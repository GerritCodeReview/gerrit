load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "logging",
    srcs = glob(
        ["*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:gson",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/log:log4j",
    ],
)
