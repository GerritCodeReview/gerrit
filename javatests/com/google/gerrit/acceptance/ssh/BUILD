load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "ssh",
    labels = ["ssh"],
    vm_args = ["-Xmx512m"],
    deps = ["//lib/commons:compress"],
)
