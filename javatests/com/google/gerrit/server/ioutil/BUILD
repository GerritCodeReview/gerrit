load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "ioutil_tests",
    size = "small",
    srcs = glob(
        ["**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/server/ioutil",
        "//lib:guava",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
