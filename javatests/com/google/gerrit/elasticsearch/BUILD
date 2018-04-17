load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = ["ElasticTestUtils.java"],
    deps = [
        "//java/com/google/gerrit/elasticsearch",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server",
        "//lib:gson",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
        "//lib/elasticsearch",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)

ELASTICSEARCH_TESTS = {i: "ElasticQuery" + i.capitalize() + "sTest.java" for i in [
    "account",
    "change",
    "group",
    "project",
]}

[junit_tests(
    name = "elasticsearch_%ss_test" % name,
    size = "large",
    srcs = [src],
    tags = [
        "elastic",
    ],
    deps = [
        ":elasticsearch_test_utils",
        "//java/com/google/gerrit/elasticsearch",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//javatests/com/google/gerrit/server/query/%s:abstract_query_tests" % name,
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
) for name, src in ELASTICSEARCH_TESTS.items()]
