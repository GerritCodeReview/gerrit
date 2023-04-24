load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*Test.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/mem",
        "//lib:jgit",
        "//lib:junit",
        "//lib/guice",
        "//lib/truth",
    ],
)
