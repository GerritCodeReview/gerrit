load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:junit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "@jgit-lib//jar",
        "@servlet-api-3_1//jar",
    ],
)
