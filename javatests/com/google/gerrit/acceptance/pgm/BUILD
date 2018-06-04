load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticReindexIT.java"],
    ),
    group = "pgm",
    labels = [
        "pgm",
        "no_windows",
    ],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/schema",
    ],
)

acceptance_tests(
    srcs = ["ElasticReindexIT.java"],
    group = "elastic",
    labels = [
        "elastic",
        "pgm",
        "no_windows",
    ],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/schema",
    ],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = [
        "AbstractReindexTests.java",
        "IndexUpgradeController.java",
    ],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
