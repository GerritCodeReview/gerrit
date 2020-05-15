load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    groups = [
        "git",
        "upload-archive",
    ],
    labels = [
        "git-protocol-v2",
        "git-upload-archive",
    ],
)
