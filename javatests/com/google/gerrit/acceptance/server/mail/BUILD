load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

DEPS = [
    "//lib/greenmail",
    "//lib/mail",
    "//java/com/google/gerrit/mail",
]

POST_JDK8_DEPS = ["//lib:javax-activation"]

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
    deps = DEPS + [":util"] + select({
        "//:java9": POST_JDK8_DEPS,
        "//:java_next": POST_JDK8_DEPS,
        "//conditions:default": [],
    }),
)

java_library(
    name = "util",
    testonly = 1,
    srcs = ["AbstractMailIT.java"],
    deps = DEPS + ["//java/com/google/gerrit/acceptance:lib"],
)
