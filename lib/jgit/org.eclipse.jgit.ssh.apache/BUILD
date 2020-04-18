load("@rules_java//java:defs.bzl", "java_library")
load("//lib/jgit:jgit.bzl", "jgit_dep")

java_library(
    name = "jgit-ssh-apache",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = [jgit_dep("@jgit-ssh-apache//jar")],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)

