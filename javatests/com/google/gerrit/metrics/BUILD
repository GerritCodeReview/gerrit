load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

junit_tests(
    name = "field_tests",
    size = "small",
    srcs = glob(["*.java"]),
    tags = ["metrics"],
    deps = [
        "//java/com/google/gerrit/metrics",
        "//lib/truth",
    ],
)
