load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/util/time",
        "//lib:guava",
        "//lib:h2",
        "//lib:jgit",
        "//lib:junit",
        "//lib/guice",
        "//lib/mockito",
        "//lib/truth",
    ],
)
