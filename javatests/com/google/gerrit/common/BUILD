load("//tools/bzl:junit.bzl", "junit_tests")

SERVER_TEST_SRCS = [
    "AutoValueTest.java",
    "VersionTest.java",
]

junit_tests(
    name = "client_tests",
    srcs = glob(
        ["**/*.java"],
        exclude = SERVER_TEST_SRCS,
    ),
    deps = [
        "//java/com/google/gerrit/common:client",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
    ],
)

junit_tests(
    name = "server_tests",
    srcs = SERVER_TEST_SRCS,
    deps = [
        "//java/com/google/gerrit/common:server",
        "//lib:truth",
        "//lib/auto:auto-value",
    ],
)
