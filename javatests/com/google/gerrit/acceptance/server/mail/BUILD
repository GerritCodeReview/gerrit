load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

DEPS = [
    "//lib/greenmail",
    "//lib/mail",
    "//java/com/google/gerrit/mail",
]

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = [
        "no_windows",
        "server",
    ],
    deps = DEPS + [":util"],
) for f in glob(
    ["*IT.java"],
    exclude = ["AbstractMailIT.java"],
)]

java_library(
    name = "util",
    testonly = True,
    srcs = ["AbstractMailIT.java"],
    deps = DEPS + ["//java/com/google/gerrit/acceptance:lib"],
)
