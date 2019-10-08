load("@rules_java//java:defs.bzl", "java_library")

package(default_testonly = 1)

java_library(
    name = "config",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib:jgit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
