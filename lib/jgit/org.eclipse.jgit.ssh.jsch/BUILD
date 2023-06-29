load("@rules_java//java:defs.bzl", "java_library")
load("//lib/jgit:jgit.bzl", "jgit_dep")

java_library(
    name = "jgit-ssh-jsch",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [jgit_dep("@jgit-ssh-jsch//jar")],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
