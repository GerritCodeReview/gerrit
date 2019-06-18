load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "serialize",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/proto",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
    ],
)
