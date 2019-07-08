load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

java_library(
    name = "util",
    testonly = 1,
    srcs = ["AbstractIndexTests.java"],
    deps = ["//gerrit-acceptance-tests:lib"],
)

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticIndexIT.java"],
    ),
    group = "ssh",
    labels = ["ssh"],
    deps = [
        ":util",
        "//lib/commons:compress",
    ],
)

acceptance_tests(
    srcs = ["ElasticIndexIT.java"],
    group = "elastic",
    labels = [
        "docker",
        "elastic",
        "exclusive",
        "ssh",
    ],
    deps = [
        ":util",
        "//lib/commons:compress",
    ],
)
