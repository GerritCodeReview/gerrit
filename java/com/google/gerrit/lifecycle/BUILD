load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "lifecycle",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
    ],
)
