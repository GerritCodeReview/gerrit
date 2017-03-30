load("//gerrit-acceptance-tests:tests.bzl", "acceptance_tests")

PKG = "com.google.gerrit.acceptance.rest.change"

SUBMIT_UTIL_SRCS = glob(["AbstractSubmit*.java"])

SUBMIT_TESTS = glob(["Submit*IT.java"])

OTHER_TESTS = glob(
    ["*IT.java"],
    exclude = SUBMIT_TESTS,
)

acceptance_tests(
    srcs = OTHER_TESTS,
    labels = ["rest"],
    pkg = PKG,
    deps = [
        ":submit_util",
        "//lib/joda:joda-time",
    ],
)

acceptance_tests(
    srcs = SUBMIT_TESTS,
    labels = ["rest"],
    pkg = PKG,
    deps = [
        ":submit_util",
    ],
)

java_library(
    name = "submit_util",
    testonly = 1,
    srcs = SUBMIT_UTIL_SRCS,
    deps = [
        "//gerrit-acceptance-tests:lib",
    ],
)
