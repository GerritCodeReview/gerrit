load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

java_library(
    name = "util",
    testonly = True,
    srcs = glob(
        ["*IndexTests.java"],
    ),
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["ElasticIndexIT.java"],
    ),
    group = "ssh",
    labels = ["ssh"],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/logging",
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
        "//java/com/google/gerrit/elasticsearch",
        "//javatests/com/google/gerrit/elasticsearch:elasticsearch_test_utils",
        "//lib/commons:compress",
    ],
)
