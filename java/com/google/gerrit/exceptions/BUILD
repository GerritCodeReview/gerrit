load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "exceptions",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//lib:jgit",
        "//lib/errorprone:annotations",
    ],
)
