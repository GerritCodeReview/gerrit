load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "cli",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:args4j",
        "//lib:guava",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)
