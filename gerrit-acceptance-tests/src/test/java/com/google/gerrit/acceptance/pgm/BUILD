load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

java_library(
    name = "reindex_tests",
    testonly = 1,
    srcs = ["AbstractReindexTests.java"],
    deps = [
        "//gerrit-acceptance-tests:lib",
    ],
)

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticReindexIT.java"],
    ),
    group = "pgm",
    labels = ["pgm"],
    deps = [":reindex_tests"],
)

acceptance_tests(
    srcs = [
        "ElasticReindexIT.java",
    ],
    group = "elastic",
    labels = [
        "docker",
        "elastic",
        "pgm",
    ],
    deps = [":reindex_tests"],
)
