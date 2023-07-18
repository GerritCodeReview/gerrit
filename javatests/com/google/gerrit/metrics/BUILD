load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "metrics_tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    tags = ["metrics"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/metrics/dropwizard",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/guice",
        "//lib/mockito",
        "//lib/truth",
        "@dropwizard-core//jar",
    ],
)
