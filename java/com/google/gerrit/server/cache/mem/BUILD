load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "mem",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:caffeine",
        "//lib:caffeine-guava",
        "//lib:guava",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
