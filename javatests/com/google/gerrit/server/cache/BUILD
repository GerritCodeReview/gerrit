load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:junit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
