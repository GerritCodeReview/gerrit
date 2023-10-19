load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "rules_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib/truth",
    ],
)
