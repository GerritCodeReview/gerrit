load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

SUBMIT_UTIL_SRCS = glob(["AbstractSubmit*.java"])

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    labels = ["rest"],
    deps = [
        ":submit_util",
        "//java/com/google/gerrit/mail",
    ],
) for f in glob(["*IT.java"])]

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
