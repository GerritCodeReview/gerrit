load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "sshd_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/sshd",
        "//lib/mina:sshd",
        "//lib/truth",
    ],
)
