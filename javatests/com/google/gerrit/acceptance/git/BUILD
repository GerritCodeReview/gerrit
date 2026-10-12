load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["git"],
    # ImplicitMergeOnSubmitExperimentsIT restarts the server for each of its
    # many config combinations and needs more heap than the default.
    vm_args = ["-Xmx512m"] if f == "ImplicitMergeOnSubmitExperimentsIT.java" else ["-Xmx256m"],
    deps = [
        ":push_for_review",
        ":submodule_util",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/server/git/receive/testing",
        "//lib/commons:lang3",
    ],
) for f in glob(["*IT.java"])]

java_library(
    name = "push_for_review",
    testonly = True,
    srcs = glob(["Abstract*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/git",
    ],
)

java_library(
    name = "submodule_util",
    testonly = True,
    srcs = ["AbstractSubmoduleSubscription.java"],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)
