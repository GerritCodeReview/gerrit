load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@maven_deps//flogger-google-extensions/jar",
        "@maven_deps//flogger-log4j-backend/jar",
        "@maven_deps//flogger-system-backend/jar",
        "@maven_deps//flogger/jar",
    ],
)
