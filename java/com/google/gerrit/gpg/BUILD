load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gpg",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//lib/bouncycastle:bcpg",
    ],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-factory",
        "//lib/bouncycastle:bcpg-neverlink",
        "//lib/bouncycastle:bcprov-neverlink",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)
