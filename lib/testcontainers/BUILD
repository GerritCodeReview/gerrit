java_library(
    name = "duct-tape",
    testonly = True,
    data = ["//lib:LICENSE-testcontainers"],
    visibility = ["//visibility:public"],
    exports = ["@duct-tape//jar"],
)

java_library(
    name = "visible-assertions",
    testonly = True,
    data = ["//lib:LICENSE-testcontainers"],
    visibility = ["//visibility:public"],
    exports = ["@visible-assertions//jar"],
)

java_library(
    name = "jna",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jna//jar"],
)

java_library(
    name = "testcontainers",
    testonly = True,
    data = ["//lib:LICENSE-testcontainers"],
    visibility = ["//visibility:public"],
    exports = ["@testcontainers//jar"],
    runtime_deps = [
        ":duct-tape",
        ":jna",
        ":visible-assertions",
        "//lib/log:ext",
    ],
)
