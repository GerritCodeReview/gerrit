package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "server",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:protobuf",
        "//proto:entities_java_proto",
    ],
)
