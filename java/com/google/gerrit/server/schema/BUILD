load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "schema",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/gpg",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-archive",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:dbcp",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/log:jsonevent-layout",
        "//lib/log:log4j",
    ],
)
