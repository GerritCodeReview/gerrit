load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "api_account",
    labels = [
        "api",
        "no_windows",
    ],
    deps = [
        ":util",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/mail",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:test-ref-update-context",
    ],
)

java_library(
    name = "util",
    testonly = True,
    srcs = glob(["TestRealm.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
    ],
)
