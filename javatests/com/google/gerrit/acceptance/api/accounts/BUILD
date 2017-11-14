load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_account",
    labels = [
        "api",
        "noci",
        "no_windows",
    ],
    deps = ["//java/com/google/gerrit/mail"],
)
