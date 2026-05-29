load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "entities",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/auto:auto-value-gson",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//proto:cache_java_proto",
        "//proto:entities_java_proto",
    ],
)
