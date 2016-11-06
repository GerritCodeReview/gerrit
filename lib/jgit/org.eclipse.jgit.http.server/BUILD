java_library(
    name = "jgit-servlet",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [":jgit-servlet-alias"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

alias(
    name = "jgit-servlet-alias",
    # Swap these lines to route jgit-servlet dependency to development tree
    actual = "@jgit_servlet//jar",
    # actual = '@jgit//org.eclipse.jgit.http.server:jgit-servlet',
    visibility = ["//visibility:public"],
)
