load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//visibility:private"],
)

java_library(
    name = "mockito",
    data = ["//lib:LICENSE-mockito"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_mockito_mockito_core"],
    runtime_deps = [
        ":byte-buddy",
        ":byte-buddy-agent",
        ":objenesis",
    ],
)

java_library(
    name = "byte-buddy",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:net_bytebuddy_byte_buddy"],
)

java_library(
    name = "byte-buddy-agent",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:net_bytebuddy_byte_buddy_agent"],
)

java_library(
    name = "objenesis",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_objenesis_objenesis"],
)
