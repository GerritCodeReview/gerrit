load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_checker",
    labels = [
        "api",
        "noci",
    ],
    deps = [
        "//java/com/google/gerrit/plugins/checkers",
        "//java/com/google/gerrit/plugins/checkers/acceptance",
        "//java/com/google/gerrit/plugins/checkers/acceptance/testsuite",
        "//java/com/google/gerrit/server/testing",
        "//java/com/google/gerrit/server/util/time",
    ],
)
