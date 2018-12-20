package(
    default_testonly = True,
    default_visibility = ["//visibility:private"],
)

java_library(
    name = "mockito",
    data = ["//lib:LICENSE-Apache2.0"],
    # Only exposed for plugin tests; core tests should use Easymock
    visibility = ["//java/com/google/gerrit/acceptance:__pkg__"],
    exports = ["@mockito//jar"],
    runtime_deps = [
        ":byte-buddy",
        ":byte-buddy-agent",
        ":objenesis",
    ],
)

java_library(
    name = "byte-buddy",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@byte-buddy//jar"],
)

java_library(
    name = "byte-buddy-agent",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@byte-buddy-agent//jar"],
)

java_library(
    name = "objenesis",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@objenesis//jar"],
)
