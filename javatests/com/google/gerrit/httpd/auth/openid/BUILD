load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "openid_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/httpd/auth/openid",
        "//java/com/google/gerrit/server",
        "//javatests/com/google/gerrit/util/http/testutil",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/guice",
        "//lib/mockito",
        "//lib/openid:consumer",
        "//lib/truth",
    ],
)
