load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    labels = [
        "notedb",
        "server",
    ],
    pkg = "com.google.gerrit.acceptance.server.notedb",
)
