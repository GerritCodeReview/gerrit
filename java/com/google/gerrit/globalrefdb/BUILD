load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "globalrefdb",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib/flogger:api",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
