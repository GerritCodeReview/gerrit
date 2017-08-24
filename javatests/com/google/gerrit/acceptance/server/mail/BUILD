load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

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
    group = "server_mail",
    labels = ["server"],
    deps = DEPS + [":util"],
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["AbstractMailIT.java"],
    deps = DEPS + ["//javatests/com/google/gerrit/acceptance:lib"],
)
