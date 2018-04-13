java_library(
    name = "elasticsearch",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-extension-api:api",
        "//gerrit-index:index",
        "//gerrit-index:query_exception",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:protobuf",
        "//lib/commons:codec",
        "//lib/commons:lang",
        "//lib/elasticsearch",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/jest",
        "//lib/jest:jest-common",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/joda:joda-time",
        "//lib/log:api",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core",
    ],
)

load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = glob(["src/test/java/**/ElasticTestUtils.java"]),
    deps = [
        ":elasticsearch",
        "//gerrit-extension-api:api",
        "//gerrit-index:index",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
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
    srcs = glob(["src/test/java/**/*Test.java"]),
    tags = [
        "elastic",
    ],
    deps = [
        ":elasticsearch",
        ":elasticsearch_test_utils",
        "//gerrit-server:query_tests_code",
        "//gerrit-server:server",
        "//gerrit-server:testutil",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
