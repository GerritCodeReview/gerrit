load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_project",
    labels = ["server"],
    deps = ["//java/com/google/gerrit/mail"],
)
