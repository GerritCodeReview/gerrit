load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticReindexIT.java"],
    ),
    group = "pgm",
    labels = ["pgm"],
    deps = [":util"],
)

acceptance_tests(
    srcs = [
        "ElasticReindexIT.java",
    ],
    group = "elastic",
    labels = [
        "docker",
        "elastic",
        "exclusive",
        "pgm",
    ],
    deps = [":util"],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["AbstractReindexTests.java"],
    deps = ["//gerrit-acceptance-tests:lib"],
)
