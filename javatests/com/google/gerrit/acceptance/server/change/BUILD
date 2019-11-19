load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_change",
    labels = ["server"],
    deps = [
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/util/time",
    ],
)
