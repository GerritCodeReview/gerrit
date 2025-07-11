load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@nongoogle_deps//:com_google_flogger_google_extensions",
        "@nongoogle_deps//:com_google_flogger_flogger_log4j_backend",
        "@nongoogle_deps//:com_google_flogger_flogger_system_backend",
        "@nongoogle_deps//:com_google_flogger_flogger",
    ],
)
