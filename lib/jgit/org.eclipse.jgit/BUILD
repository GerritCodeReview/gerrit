load("//lib/jgit:jgit.bzl", "jgit_dep")

java_library(
    name = "jgit",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [jgit_dep("@jgit-lib//jar")],
    runtime_deps = [
        ":javaewah",
        "//lib/log:api",
    ],
)

alias(
    name = "jgit-source",
    actual = jgit_dep("@jgit-lib//jar:src"),
    visibility = ["//visibility:public"],
)

java_library(
    name = "javaewah",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@javaewah//jar"],
)
