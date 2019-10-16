load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "common-data-test-util",
    testonly = True,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//lib/truth",
    ],
)
