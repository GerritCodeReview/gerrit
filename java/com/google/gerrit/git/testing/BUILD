load("@rules_java//java:defs.bzl", "java_library")

package(default_testonly = True)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
