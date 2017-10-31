load("//tools/bzl:junit.bzl", "junit_tests")

AUTO_VALUE_TEST_SRCS = ["AutoValueTest.java"]

junit_tests(
    name = "client_tests",
    srcs = glob(
        ["**/*.java"],
        exclude = AUTO_VALUE_TEST_SRCS,
    ),
    deps = [
        "//java/com/google/gerrit/common:client",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
    ],
)

junit_tests(
    name = "auto_value_tests",
    srcs = AUTO_VALUE_TEST_SRCS,
    deps = [
        "//lib:truth",
        "//lib/auto:auto-value",
    ],
)
