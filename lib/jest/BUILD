package(default_visibility = ["//visibility:public"])

java_library(
    name = "jest-common",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jest_common//jar"],
    runtime_deps = [
        "//lib/commons:lang3",
    ],
)

java_library(
    name = "jest",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jest//jar"],
    runtime_deps = [
        "//lib/httpcomponents:httpasyncclient",
        "//lib/httpcomponents:httpclient",
        "//lib/httpcomponents:httpcore-nio",
    ],
)
