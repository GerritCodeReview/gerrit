load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "metrics_tests",
    srcs = glob(["**/*.java"]),
    tags = ["metrics"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/metrics/dropwizard",
        "//lib/truth",
    ],
)
