load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

java_library(
    name = "util",
    testonly = True,
    srcs = ["AbstractIndexTests.java"],
    deps = ["//java/com/google/gerrit/acceptance:lib"],
)

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
    ),
    group = "ssh",
    labels = ["ssh"],
    vm_args = ["-Xmx512m"],
    deps = [
        ":util",
        "//java/com/google/gerrit/server/cancellation",
        "//java/com/google/gerrit/server/logging",
        "//lib/commons:compress",
    ],
)
