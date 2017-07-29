load("//tools/bzl:junit.bzl", "junit_tests")

SERVER_TEST_SRCS = [
    "AutoValueTest.java",
    "VersionTest.java",
]

junit_tests(
    name = "server_tests",
    srcs = SERVER_TEST_SRCS,
    tags = ["no_windows"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/common:version",
        "//java/com/google/gerrit/launcher",
        "//lib:guava",
        "//lib:truth",
        "//lib/auto:auto-value",
    ],
)
