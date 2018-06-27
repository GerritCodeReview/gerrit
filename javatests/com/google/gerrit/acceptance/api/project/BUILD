load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_project",
    deps = ["//java/com/google/gerrit/index/project"],
    labels = ["api"],
)
