java_library(
    name = "duct-tape",
    testonly = 1,
    data = ["//lib:LICENSE-testcontainers"],
    visibility = ["//visibility:public"],
    exports = ["@duct-tape//jar"],
)

java_library(
    name = "visible-assertions",
    testonly = 1,
    data = ["//lib:LICENSE-testcontainers"],
    visibility = ["//visibility:public"],
    exports = ["@visible-assertions//jar"],
)

java_library(
    name = "jna",
    testonly = 1,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@jna//jar"],
)

java_library(
    name = "testcontainers",
    testonly = 1,
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

java_library(
    name = "testcontainers-elasticsearch",
    testonly = 1,
    data = ["//lib:LICENSE-testcontainers"],
    visibility = ["//visibility:public"],
    exports = ["@testcontainers-elasticsearch//jar"],
    runtime_deps = [":testcontainers"],
)
