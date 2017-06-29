load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "pgm",
    labels = ["pgm"],
    deps = [":util"],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["IndexUpgradeController.java"],
    deps = ["//gerrit-acceptance-tests:lib"],
)
