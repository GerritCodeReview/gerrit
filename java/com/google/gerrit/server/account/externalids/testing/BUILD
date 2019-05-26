load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "testing",
    testonly = 1,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
