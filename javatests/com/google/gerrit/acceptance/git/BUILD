load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

ABSTRACT_TEST_DEFINITIONS_SRC = glob(["Abstract*.java"])

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ABSTRACT_TEST_DEFINITIONS_SRC,
    ),
    group = "git",
    labels = ["git"],
    deps = [":abstract_test_definitions"],
)

java_library(
    name = "abstract_test_definitions",
    testonly = 1,
    srcs = ABSTRACT_TEST_DEFINITIONS_SRC,
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
