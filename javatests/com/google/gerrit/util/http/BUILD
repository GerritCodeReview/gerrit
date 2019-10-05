load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "http_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/util/http",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:junit",
        "//lib:servlet-api-without-neverlink",
        "//lib/truth",
    ],
)
