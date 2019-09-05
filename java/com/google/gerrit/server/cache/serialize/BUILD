load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "serialize",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gwtorm",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
