java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [":jgit-archive-alias"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

alias(
    name = "jgit-archive-alias",
    actual = select({
#        "@//lib:jgit-dev": "@jgit//org.eclipse.jgit.archive:jgit-archive",
        "//conditions:default": "@jgit_archive//jar",
    }),
    visibility = ["//visibility:public"],
)
