load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "dropwizard",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//lib:args4j",
        "//lib:guava",
        "//lib/dropwizard:dropwizard-core",
        "//lib/flogger:api",
        "//lib/guice",
    ],
)
