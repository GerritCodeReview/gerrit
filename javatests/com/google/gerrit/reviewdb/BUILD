load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "client_tests",
    srcs = glob(["client/**/*.java"]),
    deps = [
        "//gerrit-server:testutil",
        "//java/com/google/gerrit/reviewdb:client",
        "//lib:gwtorm",
        "//lib:truth",
    ],
)
