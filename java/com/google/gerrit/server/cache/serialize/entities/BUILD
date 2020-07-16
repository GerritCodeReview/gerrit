load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "entities",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server/cache/serialize",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//proto:cache_java_proto",
    ],
)
