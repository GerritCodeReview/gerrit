load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_change",
    labels = ["server"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/util/time",
    ],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["CommentsUtil.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
    ],
)
