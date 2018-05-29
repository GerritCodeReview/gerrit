load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticReindexIT.java"],
    ),
    group = "pgm",
    labels = ["pgm"],
    vm_args = ["-Xmx512m"],
    deps = [":util"],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["IndexUpgradeController.java"],
    deps = ["//gerrit-acceptance-tests:lib"],
)

acceptance_tests(
    srcs = [
        "AbstractReindexIT.java",
        "ElasticReindexIT.java",
    ],
    group = "elastic",
    labels = [
        "docker",
        "elastic",
        "pgm",
    ],
)
