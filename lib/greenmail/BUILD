load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "javax-activation",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@maven_deps//javax-activation/jar"],
)

java_library(
    name = "greenmail",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@maven_deps//greenmail/jar"],
    runtime_deps = [":javax-activation"],
)
