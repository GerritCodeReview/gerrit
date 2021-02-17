load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "data",
    srcs = glob(
        ["*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//lib:gson",
    ],
)
