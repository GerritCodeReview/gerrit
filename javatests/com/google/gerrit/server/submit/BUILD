load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "submit_tests",
    size = "small",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//lib/truth:truth-proto-extension",
    ],
)
