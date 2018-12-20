load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "json_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/json",
        "//lib:guava",
        "//lib/truth",
    ],
)
