package(default_visibility = ["//visibility:public"])

java_library(
    name = "mockito",
    testonly = 1,
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@mockito//jar"],
    runtime_deps = [
        ":byte-buddy",
        ":byte-buddy-agent",
        ":objenesis",
    ],
)

java_library(
    name = "byte-buddy",
    testonly = 1,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@byte-buddy//jar"],
)

java_library(
    name = "byte-buddy-agent",
    testonly = 1,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@byte-buddy-agent//jar"],
)

java_library(
    name = "objenesis",
    testonly = 1,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@objenesis//jar"],
)
