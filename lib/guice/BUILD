load("@rules_java//java:defs.bzl", "java_import", "java_library")

java_import(
    name = "guice-file",
    jars = ["@guice-library//file"],
)

java_library(
    name = "guice",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [
        ":guice-library",
        ":javax_inject",
    ],
)

java_library(
    name = "guice-library",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = [":guice-file"],
    runtime_deps = ["aopalliance"],
)

java_library(
    name = "guice-assistedinject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@guice-assistedinject//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "guice-servlet",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@guice-servlet//jar"],
    runtime_deps = [":guice"],
)

java_library(
    name = "aopalliance",
    data = ["//lib:LICENSE-PublicDomain"],
    exports = ["@aopalliance//jar"],
)

java_library(
    name = "javax_inject",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@javax_inject//jar"],
)
