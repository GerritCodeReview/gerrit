load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "ioutil_tests",
    size = "small",
    srcs = glob(
        ["**/*.java"],
    ),
    resource_strip_prefix = "resources",
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/server/ioutil",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
