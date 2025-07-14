load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "api",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        "@gerrit_deps//:com_google_flogger_flogger",
        "@gerrit_deps//:com_google_flogger_flogger_log4j_backend",
        "@gerrit_deps//:com_google_flogger_flogger_system_backend",
        "@gerrit_deps//:com_google_flogger_google_extensions",
    ],
)
