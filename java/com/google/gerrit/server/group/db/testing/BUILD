load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "testing",
    testonly = True,
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:test-ref-update-context",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/errorprone:annotations",
    ],
)
