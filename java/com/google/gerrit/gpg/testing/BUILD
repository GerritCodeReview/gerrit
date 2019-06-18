load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gpg-test-util",
    testonly = True,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/gpg",
        "//lib:guava",
        "//lib:jgit",
        "//lib/bouncycastle:bcpg-neverlink",
    ],
)
