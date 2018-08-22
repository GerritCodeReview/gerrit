package(default_testonly = 1)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/server/cache/serialize",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/commons:lang3",
        "//lib/truth",
    ],
)
