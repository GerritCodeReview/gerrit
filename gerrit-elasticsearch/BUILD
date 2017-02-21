java_library(
    name = "elasticsearch",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-antlr:query_exception",
        "//gerrit-extension-api:api",
        "//gerrit-reviewdb:client",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:protobuf",
        "//lib/commons:codec",
        "//lib/commons:lang",
        "//lib/elasticsearch",
        "//lib/elasticsearch:jest",
        "//lib/elasticsearch:jest-common",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
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
        "//gerrit-reviewdb:client",
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

