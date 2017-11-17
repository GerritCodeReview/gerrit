load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@flogger-log4j2-backend//jar",
        "@flogger-system-backend//jar",
        "@flogger//jar",
    ],
)
