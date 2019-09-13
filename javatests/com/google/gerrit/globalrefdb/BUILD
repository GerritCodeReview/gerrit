load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:license.bzl", "license_test")

junit_tests(
    name = "globalrefdb_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/globalrefdb",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/truth",
        "//lib:guava",
        "//lib:junit",
        "//lib/easymock",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
