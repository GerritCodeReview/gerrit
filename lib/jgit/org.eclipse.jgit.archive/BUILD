load("//lib/jgit:jgit.bzl", "jgit_dep")

java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [jgit_dep("@jgit-archive//jar")],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
