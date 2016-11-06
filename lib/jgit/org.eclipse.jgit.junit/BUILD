java_library(
    name = "junit",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [":junit-alias"],
    testonly = 1,
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

alias(
    name = "junit-alias",
    actual = select({
        "@//lib:jgit-dev": "@jgit//org.eclipse.jgit.junit:junit",
        "//conditions:default": "@jgit_junit//jar",
    }),
    testonly = 1,
    visibility = ["//visibility:public"],
)
