load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "jgit-servlet",
    data = ["//lib:LICENSE-jgit"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.http.server:jgit-servlet"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
