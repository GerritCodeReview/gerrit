load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "time",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
