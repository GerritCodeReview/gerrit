load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")

ABSTRACT_QUERY_TEST = ["AbstractQueryGroupsTest.java"]

java_library(
    name = "abstract_query_tests",
    testonly = True,
    srcs = ABSTRACT_QUERY_TEST,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:log4j",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
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
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
