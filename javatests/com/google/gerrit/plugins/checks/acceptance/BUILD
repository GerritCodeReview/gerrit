package(default_visibility = ["//java/com/google/gerrit/plugins/checks:visibility"])

load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "checks",
    deps = [
        "//java/com/google/gerrit/plugins/checks:checks__plugin",
        "//java/com/google/gerrit/plugins/checks/acceptance",
        "//java/com/google/gerrit/plugins/checks/acceptance/testsuite",
        "//java/com/google/gerrit/server/util/time",
        "//javatests/com/google/gerrit/acceptance/rest/util",
    ],
)
