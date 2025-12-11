load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")

junit_tests(
    name = "testing_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/git/testing",
        "//lib/truth",
    ],
)
