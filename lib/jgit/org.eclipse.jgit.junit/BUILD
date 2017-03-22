java_library(
    name = "junit",
    testonly = 1,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = select({
        # "//lib/jgit:dev": ["@jgit//org.eclipse.jgit.junit:junit"],
        "//conditions:default": ["@jgit_junit//jar"],
    }),
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
