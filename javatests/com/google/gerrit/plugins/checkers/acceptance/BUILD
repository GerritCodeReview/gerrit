package(default_visibility = ["//java/com/google/gerrit/plugins/checkers:visibility"])

load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "checkers",
    deps = [
        "//java/com/google/gerrit/plugins/checkers:checkers__plugin",
        "//java/com/google/gerrit/plugins/checkers/acceptance",
        "//java/com/google/gerrit/plugins/checkers/acceptance/testsuite",
        "//java/com/google/gerrit/server/util/time",
        "//javatests/com/google/gerrit/acceptance/rest/util",
    ],
)
