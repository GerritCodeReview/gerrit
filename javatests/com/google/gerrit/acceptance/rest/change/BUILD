load("@rules_java//java:defs.bzl", "java_library")
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
        "//java/com/google/gerrit/entities/converter:converters",
        "//java/com/google/gerrit/mail",
    ],
)

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["rest"],
    deps = [
        ":submit_util",
    ],
) for f in SUBMIT_TESTS]

java_library(
    name = "submit_util",
    testonly = True,
    srcs = SUBMIT_UTIL_SRCS,
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/util/time",
    ],
)
