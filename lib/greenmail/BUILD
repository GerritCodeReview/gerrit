load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "javax-activation",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@gerrit-maven//:javax_activation_activation"],
)

java_library(
    name = "greenmail",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@gerrit-maven//:com_icegreen_greenmail"],
    runtime_deps = [":javax-activation"],
)
