load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "test_util",
    testonly = 1,
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-extension-api:api",
        "//lib:truth",
    ],
)
