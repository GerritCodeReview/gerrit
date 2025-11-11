load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//visibility:private"],
)

java_library(
    name = "mockito",
    data = ["//lib:LICENSE-mockito"],
    visibility = ["//visibility:public"],
    exports = ["@maven_deps//mockito/jar"],
    runtime_deps = [
        ":byte-buddy",
        ":byte-buddy-agent",
        ":objenesis",
    ],
)

java_library(
    name = "byte-buddy",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//bytebuddy/jar"],
)

java_library(
    name = "byte-buddy-agent",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//bytebuddy-agent/jar"],
)

java_library(
    name = "objenesis",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//objenesis/jar"],
)
