load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "time",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/server/util/git",
        "//lib:guava",
        "//lib:jgit",
    ],
)
