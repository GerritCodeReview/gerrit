load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["**/*IT.java"]),
    group = "server_index",
    labels = ["server"],
    deps = ["//lib/dropwizard:dropwizard-core"],
)
