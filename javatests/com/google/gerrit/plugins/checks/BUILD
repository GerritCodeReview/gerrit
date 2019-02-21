package(default_visibility = ["//java/com/google/gerrit/plugins/checks:visibility"])

load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "checks_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/plugins/checks:checks__plugin",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)
