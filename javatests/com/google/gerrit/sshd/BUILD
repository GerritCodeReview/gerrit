load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "sshd_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/sshd",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/guice",
        "//lib/mina:sshd",
        "//lib/truth",
        "//lib:jgit",
    ],
)
