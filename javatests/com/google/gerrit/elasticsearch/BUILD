load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = ["ElasticTestUtils.java"],
    deps = [
        "//java/com/google/gerrit:server",
        "//java/com/google/gerrit/elasticsearch",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
        "//lib/elasticsearch",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)

junit_tests(
    name = "elasticsearch_tests",
    size = "large",
    srcs = glob(["**/*Test.java"]),
    tags = [
        "elastic",
    ],
    deps = [
        ":elasticsearch_test_utils",
        "//java/com/google/gerrit:server",
        "//java/com/google/gerrit/elasticsearch",
        "//javatests/com/google/gerrit:query_tests_code",
        "//javatests/com/google/gerrit:testutil",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
