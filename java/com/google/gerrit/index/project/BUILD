load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "project",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//lib:guava",
        "//lib/guice",
    ],
)
