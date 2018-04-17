load("//tools/bzl:junit.bzl", "junit_tests")

ABSTRACT_QUERY_TEST = ["AbstractQueryProjectsTest.java"]

java_library(
    name = "abstract_query_tests",
    testonly = 1,
    srcs = ABSTRACT_QUERY_TEST,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

junit_tests(
    name = "lucene_query_test",
    size = "large",
    srcs = glob(
        ["*.java"],
        exclude = ABSTRACT_QUERY_TEST,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":abstract_query_tests",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
