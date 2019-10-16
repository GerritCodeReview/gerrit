load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "entities_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/truth",
    ],
)
