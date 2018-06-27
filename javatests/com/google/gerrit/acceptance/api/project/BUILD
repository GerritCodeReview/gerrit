load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_project",
    labels = ["api"],
    deps = ["//java/com/google/gerrit/index/project"],
)
