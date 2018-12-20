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
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/server/schema",
    ],
)

acceptance_tests(
    srcs = ["ElasticReindexIT.java"],
    group = "elastic",
    labels = [
        "docker",
        "elastic",
        "exclusive",
        "flaky",
        "pgm",
        "no_windows",
    ],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/elasticsearch",
        "//java/com/google/gerrit/server/schema",
        "//javatests/com/google/gerrit/elasticsearch:elasticsearch_test_utils",
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
