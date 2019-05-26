load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "server",
    srcs = glob(["common/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
