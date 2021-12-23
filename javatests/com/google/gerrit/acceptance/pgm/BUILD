load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
    ),
    group = "pgm",
    labels = [
        "pgm",
        "no_windows",
    ],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/schema",
        "//lib/errorprone:annotations",
    ],
)

java_library(
    name = "util",
    testonly = True,
    srcs = [
        "AbstractReindexTests.java",
        "IndexUpgradeController.java",
    ],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
