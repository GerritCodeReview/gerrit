load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticReindexIT.java"],
    ),
    group = "pgm",
    labels = ["pgm"],
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
