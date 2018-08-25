package(default_visibility = ["//visibility:public"])

POST_JDK8_DEPS = [":javax-activation"]

java_library(
    name = "javax-activation",
    testonly = 1,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@javax-activation//jar"],
)

java_library(
    name = "greenmail",
    testonly = True,
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@greenmail//jar"],
    runtime_deps = select({
        "//:java9": POST_JDK8_DEPS,
        "//:java_next": POST_JDK8_DEPS,
        "//conditions:default": [],
    }),
)
