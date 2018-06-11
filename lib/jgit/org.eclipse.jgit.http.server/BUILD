load("//lib/jgit:jgit.bzl", "jgit_dep")

java_library(
    name = "jgit-servlet",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [jgit_dep("@jgit-servlet//jar")],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
