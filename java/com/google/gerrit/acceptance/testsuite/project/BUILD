load("@rules_java//java:defs.bzl", "java_library")

package(default_testonly = 1)

java_library(
    name = "project",
    testonly = True,
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/acceptance:function",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:test-ref-update-context",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/commons:lang3",
        "//lib/errorprone:annotations",
        "//lib/guice",
    ],
)
