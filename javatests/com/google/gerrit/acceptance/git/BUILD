load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "git",
    labels = ["git"],
    deps = [
        ":push_for_review",
        ":submodule_util",
    ],
)

java_library(
    name = "push_for_review",
    testonly = 1,
    srcs = ["AbstractPushForReview.java"],
    deps = [
        "//javatests/com/google/gerrit/acceptance:lib",
        "//lib/joda:joda-time",
    ],
)

java_library(
    name = "submodule_util",
    testonly = 1,
    srcs = ["AbstractSubmoduleSubscription.java"],
    deps = ["//javatests/com/google/gerrit/acceptance:lib"],
)
