java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = select({
        # "//lib/jgit:dev": ["@jgit//org.eclipse.jgit.archive:jgit-archive"],
        "//conditions:default": ["@jgit_archive//jar"],
    }),
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
