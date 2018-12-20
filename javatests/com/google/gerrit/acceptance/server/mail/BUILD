load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

DEPS = [
    "//lib/greenmail",
    "//lib/mail",
    "//java/com/google/gerrit/mail",
]

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["AbstractMailIT.java"],
    ),
    group = "server_mail",
    labels = [
        "no_windows",
        "server",
    ],
    deps = DEPS + [":util"],
)

java_library(
    name = "util",
    testonly = True,
    srcs = ["AbstractMailIT.java"],
    deps = DEPS + ["//java/com/google/gerrit/acceptance:lib"],
)
