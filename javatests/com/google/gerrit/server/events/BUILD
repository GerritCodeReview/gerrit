load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "events_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:gson",
        "//lib:guava",
        "//lib/truth",
    ],
)
