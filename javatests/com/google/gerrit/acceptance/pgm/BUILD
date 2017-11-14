load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "pgm",
    labels = ["pgm"],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/schema",
    ],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["IndexUpgradeController.java"],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
