load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "rest_config",
    labels = ["rest"],
    deps = [
        "//java/com/google/gerrit/server/restapi",
        "//lib/lucene:lucene-core",
    ],
)
