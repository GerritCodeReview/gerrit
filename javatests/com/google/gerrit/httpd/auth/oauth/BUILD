load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "oauth_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/httpd/auth/oauth",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:servlet-api",
        "//lib/guice",
        "//lib/mockito",
        "//lib/truth",
    ],
)
