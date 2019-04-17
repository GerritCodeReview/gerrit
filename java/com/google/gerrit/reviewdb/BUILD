package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "server",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gwtorm",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//proto:entities_java_proto",
    ],
)
