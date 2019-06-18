load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "h2",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/util/time",
        "//lib:guava",
        "//lib:h2",
        "//lib:jgit",
        "//lib/flogger:api",
        "//lib/guice",
    ],
)
