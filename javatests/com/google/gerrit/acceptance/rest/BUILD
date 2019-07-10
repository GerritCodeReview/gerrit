load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_bindings_collection",
    labels = ["rest"],
    deps = [
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/util/cli",
    ],
)
