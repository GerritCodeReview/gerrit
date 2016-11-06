java_library(
    name = "jgit",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [":jgit-alias"],
    runtime_deps = [":javaewah"],
)

alias(
    name = "jgit-alias",
    actual = select({
#        "@//lib:jgit-dev": "@jgit//org.eclipse.jgit:jgit",
        "//conditions:default": "@jgit_lib//jar",
    }),
    visibility = ["//visibility:public"],
)

alias(
    name = "jgit-source",
    actual = select({
#        "@//lib:jgit-dev": "@jgit//org.eclipse.jgit:libjgit-src.jar",
        "//conditions:default": "@jgit_lib//jar:src",
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "javaewah",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@javaewah//jar"],
)
