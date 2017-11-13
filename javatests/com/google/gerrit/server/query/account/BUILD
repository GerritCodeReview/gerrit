load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "lucene_query_test",
    size = "large",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
