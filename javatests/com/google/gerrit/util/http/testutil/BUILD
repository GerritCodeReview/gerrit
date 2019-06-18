load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "testutil",
    testonly = True,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/httpcomponents:httpclient",
    ],
)
