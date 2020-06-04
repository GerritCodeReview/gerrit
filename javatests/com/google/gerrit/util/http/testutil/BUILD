load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "testutil",
    testonly = True,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/httpcomponents:httpclient",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
