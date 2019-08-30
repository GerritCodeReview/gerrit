load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "mem",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-extension-api:api",
        "//gerrit-server:server",
        "//lib:guava",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
