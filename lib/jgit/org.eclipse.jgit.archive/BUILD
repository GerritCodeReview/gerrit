java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [":jgit-archive-alias"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

alias(
    name = "jgit-archive-alias",
    # Swap these lines to route jgit-archive dependency to development tree
    actual = "@jgit_archive//jar",
    # actual = '@jgit//org.eclipse.jgit.archive:jgit-archive',
    visibility = ["//visibility:public"],
)
