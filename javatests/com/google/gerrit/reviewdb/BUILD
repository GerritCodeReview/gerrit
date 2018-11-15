load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "client_tests",
    srcs = glob(["client/**/*.java"]),
    deps = [
        "//java/com/google/gerrit/reviewdb:client",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib/truth",
    ],
)
