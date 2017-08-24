load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

SUBMIT_UTIL_SRCS = glob(["AbstractSubmit*.java"])

SUBMIT_TESTS = glob(["Submit*IT.java"])

OTHER_TESTS = glob(
    ["*IT.java"],
    exclude = SUBMIT_TESTS,
)

acceptance_tests(
    srcs = OTHER_TESTS,
    group = "rest_change_other",
    labels = ["rest"],
    deps = [
        ":submit_util",
        "//lib/joda:joda-time",
    ],
)

acceptance_tests(
    srcs = SUBMIT_TESTS,
    group = "rest_change_submit",
    labels = ["rest"],
    deps = [
        ":submit_util",
    ],
)

java_library(
    name = "submit_util",
    testonly = 1,
    srcs = SUBMIT_UTIL_SRCS,
    deps = [
        "//javatests/com/google/gerrit/acceptance:lib",
    ],
)
