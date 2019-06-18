load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "jgit-archive",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.archive:jgit-archive"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
