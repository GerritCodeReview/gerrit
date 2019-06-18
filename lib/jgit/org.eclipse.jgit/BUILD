load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "jgit",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit:jgit"],
    runtime_deps = [
        ":javaewah",
        "//lib/log:api",
    ],
)

java_library(
    name = "javaewah",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@javaewah//jar"],
)
