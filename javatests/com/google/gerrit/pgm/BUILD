load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:license.bzl", "license_test")

junit_tests(
    name = "pgm_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/pgm",
        "//java/com/google/gerrit/pgm/http/jetty",
        "//java/com/google/gerrit/pgm/init",
        "//java/com/google/gerrit/pgm/init/api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/securestore/testing",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:junit",
        "//lib/easymock",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/truth",
    ],
)

license_test(
    name = "pgm_license_test",
    target = "//java/com/google/gerrit/pgm",
)
