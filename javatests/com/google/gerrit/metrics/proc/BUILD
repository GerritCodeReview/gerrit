load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proc_tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//java/com/google/gerrit/testing:gerrit-junit",
        "//lib/dropwizard:dropwizard-core",
        "//lib/guice",
        "//lib/mockito",
        "//lib/truth",
    ],
)
