load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "converters",
    srcs = glob(
        ["**/*.java"],
        exclude = [
            "ProtoConverter.java",
            "SafeProtoConverter.java",
        ],
    ),
    deps = [
        ":proto_converter",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
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

java_library(
    name = "proto_converter",
    srcs = [
        "ProtoConverter.java",
        "SafeProtoConverter.java",
    ],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:protobuf",
        "//lib/errorprone:annotations",
    ],
)
