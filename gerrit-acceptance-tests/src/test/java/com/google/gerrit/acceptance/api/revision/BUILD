load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_revision",
    labels = ["api"],
    deps = [":custom_truth_subjects"],
)

java_library(
    name = "custom_truth_subjects",
    testonly = 1,
    srcs = glob(["*Subject.java"]),
    deps = ["//gerrit-acceptance-tests:lib"],
)
