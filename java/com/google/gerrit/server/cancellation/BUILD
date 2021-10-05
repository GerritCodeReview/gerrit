load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "cancellation",
    srcs = glob(
        ["*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib/commons:lang3",
    ],
)
