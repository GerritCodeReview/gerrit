java_library(
    name = "jgit",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [":jgit-alias"],
    runtime_deps = [":javaewah"],
)

alias(
    name = "jgit-alias",
    # Swap these lines to route jgit dependency to development tree
    actual = "@jgit//jar",
    # actual = '@jgit//org.eclipse.jgit:jgit',
    visibility = ["//visibility:public"],
)

alias(
    name = "jgit-source",
    # Swap these lines to route jgit dependency to development tree
    actual = "@jgit//jar:src",
    # actual = '@jgit//org.eclipse.jgit:libjgit-src.jar',
    visibility = ["//visibility:public"],
)

java_library(
    name = "javaewah",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@javaewah//jar"],
)
