load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "testing",
    # testonly = True, make test only
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/logging",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
    ],
)
