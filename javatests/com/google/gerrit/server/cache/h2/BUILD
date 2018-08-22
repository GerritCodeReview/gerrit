load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/server/cache/serialize",
        "//lib:guava",
        "//lib:h2",
        "//lib:junit",
        "//lib/guice",
        "//lib/truth",
    ],
)
