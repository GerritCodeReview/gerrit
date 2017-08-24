load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "acceptance_framework_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//lib:guava",
        "//lib:truth",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
