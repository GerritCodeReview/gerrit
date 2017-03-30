load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

DEPS = [
    "//lib/greenmail",
    "//lib/joda:joda-time",
    "//lib/mail",
]

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["AbstractMailIT.java"],
    ),
    labels = ["server"],
    pkg = "com.google.gerrit.acceptance.server.mail",
    deps = [":util"] + DEPS,
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["AbstractMailIT.java"],
    deps = ["//gerrit-acceptance-tests:lib"] + DEPS,
)
