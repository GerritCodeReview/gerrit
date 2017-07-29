package(
    default_visibility = ["//visibility:public"],
)

SRC = "src/main/java/com/google/gerrit/reviewdb/"

TESTS = "src/test/java/com/google/gerrit/reviewdb/"

java_library(
    name = "server",
    srcs = glob([SRC + "**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-extension-api:api",
        "//lib:guava",
        "//lib:gwtorm",
    ],
)
