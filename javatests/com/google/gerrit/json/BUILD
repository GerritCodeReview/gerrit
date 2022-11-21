load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "json_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/json",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:gson",
        "//lib:guava",
        "//lib/truth",
    ],
)
