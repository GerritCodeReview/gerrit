load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/index:query_parser",
        "//lib:guava",
        "//lib:junit",
        "//lib/antlr:java-runtime",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
