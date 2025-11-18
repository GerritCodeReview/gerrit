load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "metrics_tests",
    size = "small",
    srcs = glob(["*.java"]),
    tags = ["metrics"],
    deps = [
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server/logging",
        "//lib:junit",
        "//lib/mockito",
        "//lib/truth",
    ],
)
