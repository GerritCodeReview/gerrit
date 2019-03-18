load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_change",
    labels = [
        "api",
        "noci",
    ],
    deps = ["//java/com/google/gerrit/server/util/time"],
)
