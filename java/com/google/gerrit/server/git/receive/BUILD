load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "receive",
    srcs = glob(
        ["**/*.java"],
        exclude = ["ReceivePackRefCache.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":ref_cache",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/util/cli",
        "//lib:args4j",
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)

java_library(
    name = "ref_cache",
    srcs = glob(["ReceivePackRefCache.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//lib:guava",
        "//lib:jgit",
    ],
)
