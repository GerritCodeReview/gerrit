load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_config",
    labels = ["change"],
    deps = [
        "//java/com/google/gerrit/server/restapi",
    ],
)
