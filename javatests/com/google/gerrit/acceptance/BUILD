load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "acceptance_framework_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//java/com/google/gerrit/server/util/time",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
