load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "util",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/server/cache/mem",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/util/cli",
        "//java/com/google/gerrit/util/logging",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/log:log4j",
    ],
)
