load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    labels = [
        "api",
        "noci",
    ],
    pkg = "com.google.gerrit.acceptance.api.accounts",
)
