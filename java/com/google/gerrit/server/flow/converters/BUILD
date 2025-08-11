load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "converters",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities/converter:proto_converter",
        "//java/com/google/gerrit/server/flow",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/errorprone:annotations",
        "//proto:flow_java_proto",
    ],
)
