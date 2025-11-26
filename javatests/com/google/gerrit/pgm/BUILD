load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:license.bzl", "license_test")

junit_tests(
    name = "pgm_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/pgm/http/jetty",
        "//java/com/google/gerrit/pgm/init/api",
        "//java/com/google/gerrit/pgm/util",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/securestore/testing",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:junit",
        "//lib:servlet-api",
        "//lib/guice",
        "//lib/jetty:server",
        "//lib/mockito",
        "//lib/truth",
    ],
)

license_test(
    name = "pgm_license_test",
    target = "//java/com/google/gerrit/pgm",
)
