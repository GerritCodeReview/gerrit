load("@rules_java//java:defs.bzl", "java_library")
load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

java_library(
    name = "http_test_util",
    srcs = ["HttpTestUtil.java"],
    deps = [
        "//lib:servlet-api",
    ],
)

acceptance_tests(
    srcs = glob(["*IT.java"]),
    group = "server_httpd",
    labels = ["server"],
    deps = [":http_test_util"],
)
