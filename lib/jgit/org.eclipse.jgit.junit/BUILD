load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "junit",
    testonly = True,
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@jgit//org.eclipse.jgit.junit:junit"],
    runtime_deps = ["//lib/jgit/org.eclipse.jgit:jgit"],
)
