load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "javax-activation",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@external_deps//:javax_activation_activation"],
)

java_library(
    name = "greenmail",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:com_icegreen_greenmail"],
    runtime_deps = [":javax-activation"],
)
