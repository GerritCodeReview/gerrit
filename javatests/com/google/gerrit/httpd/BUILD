load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "httpd_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/json",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:gson",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:jimfs",
        "//lib:junit",
        "//lib:servlet-api-without-neverlink",
        "//lib:soy",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/mockito",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
