load("@rules_java//java:defs.bzl", "java_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

java_library(
    name = "servlet",
    srcs = [":srcs"],
    visibility = ["//java/com/google/gerrit/server:__pkg__"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/guice",
    ],
)
