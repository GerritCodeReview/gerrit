load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "audit",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/flogger:api",
        "//lib/guice",
    ],
)
