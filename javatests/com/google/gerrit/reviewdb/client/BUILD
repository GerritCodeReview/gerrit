load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "client_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
