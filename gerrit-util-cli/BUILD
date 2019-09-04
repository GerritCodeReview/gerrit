load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "cli",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-common:annotations",
        "//gerrit-common:server",
        "//lib:args4j",
        "//lib:guava",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)
