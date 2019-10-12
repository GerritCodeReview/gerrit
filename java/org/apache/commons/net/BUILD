load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "net",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/util/ssl",
        "//lib:guava",
        "//lib/commons:net",
    ],
)
