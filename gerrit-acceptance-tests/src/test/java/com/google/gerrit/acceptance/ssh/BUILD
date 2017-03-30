load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    labels = ["ssh"],
    pkg = "com.google.gerrit.acceptance.ssh",
    deps = ["//lib/commons:compress"],
)
