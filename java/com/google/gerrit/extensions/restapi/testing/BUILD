load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "restapi-test-util",
    testonly = True,
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/truth",
        "//lib/errorprone:annotations",
        "//lib/truth",
    ],
)
