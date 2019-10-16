load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gpg",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/api",
        "//lib:guava",
        "//lib:jgit",
        "//lib/bouncycastle:bcpg-neverlink",
        "//lib/bouncycastle:bcprov-neverlink",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)
