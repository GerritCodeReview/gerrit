load("//lib/jgit:jgit.bzl", "jgit_dep")

java_library(
    name = "junit",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [jgit_dep("@jgit-junit//jar")],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
