load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = ["ElasticTestUtils.java"],
    deps = [
        "//gerrit-server:server",
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
        "//gerrit-server:query_tests_code",
        "//gerrit-server:server",
        "//gerrit-server:testutil",
        "//java/com/google/gerrit/elasticsearch",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
