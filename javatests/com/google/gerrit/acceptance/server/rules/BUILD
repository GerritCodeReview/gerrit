load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_rules",
    labels = ["server"],
    deps = [
        "@prolog-runtime//jar",
    ],
)
