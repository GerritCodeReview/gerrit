package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "server",
    srcs = glob(["**/*.java"]),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/reviewdb"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:gwtorm",
    ],
)
