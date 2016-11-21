load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_mail",
    labels = ["server"],
    deps = [
        "//lib/greenmail",
        "//lib/joda:joda-time",
        "//lib/mail",
    ],
)
