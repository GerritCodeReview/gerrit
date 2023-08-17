load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "dropwizard_tests",
    srcs = glob(["**/*.java"]),
    tags = ["metrics"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/mockito",
        "//lib/truth",
        "@dropwizard-core//jar",
    ],
)
