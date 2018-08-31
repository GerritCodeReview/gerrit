load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    size = "small",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//antlr3:query_parser",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//lib:guava",
        "//lib:junit",
        "//lib/antlr:java-runtime",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/truth",
    ],
)
