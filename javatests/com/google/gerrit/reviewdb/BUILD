load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "client_tests",
    srcs = glob(["client/**/*.java"]),
    deps = [
        "//java/com/google/gerrit/reviewdb:client",
        "//javatests/com/google/gerrit:testutil",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)
