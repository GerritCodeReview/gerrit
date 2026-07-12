load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_bindings",
    labels = ["rest"],
    deps = [
        "//java/com/google/gerrit/server/logging",
        "//javatests/com/google/gerrit/acceptance/rest/util",
    ],
)
