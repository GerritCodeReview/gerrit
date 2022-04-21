load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:junit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "@jgit-lib//jar",
        "@servlet-api//jar",
    ],
)
