load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "ssh",
    labels = ["ssh"],
    deps = ["//lib/commons:compress"],
)
