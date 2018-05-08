# TODO(ekempin): Download flogger jars from Maven (log4j backend is not available on Maven yet)
#java_library(
#    name = "api",
#    data = ["//lib:LICENSE-Apache2.0"],
#    visibility = ["//visibility:public"],
#    exports = [
#      "@flogger//jar",
#      "@flogger-log4j-backend//jar",
#      "@flogger-system-backend//jar",
#    ],
#)

java_import(
    name = "api",
    jars = [
        "libapi.jar",
        "libchecks.jar",
        "libconfig.jar",
        "liblazy_args.jar",
        "liblog4j_backend.jar",
        "libstack.jar",
        "libsystem_backend.jar",
        "libtags.jar",
    ],
    visibility = ["//visibility:public"],
)
