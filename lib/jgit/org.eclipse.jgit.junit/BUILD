java_library(
    name = "junit",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [":junit-alias"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

alias(
    name = "junit-alias",
    # Swap these lines to route jgit-archive dependency to development tree
    actual = "@jgit_junit//jar",
    # actual = '@jgit//org.eclipse.jgit.junit:junit',
    visibility = ["//visibility:public"],
)
