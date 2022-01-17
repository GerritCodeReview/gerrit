load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@flogger-google-extensions//jar",
        "@flogger-log4j-backend//jar",
        "@flogger-system-backend//jar",
        "@flogger//jar",
    ],
)
