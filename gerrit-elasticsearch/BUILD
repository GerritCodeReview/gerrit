java_library(
    name = "elasticsearch",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = ["**/testing/**"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-antlr:query_exception",
        "//gerrit-extension-api:api",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib/commons:codec",
        "//lib/elasticsearch",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/jest",
        "//lib/jest:jest-common",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
        "//lib/lucene:lucene-core",
    ],
)

load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = glob(["src/main/java/com/google/gerrit/elasticsearch/testing/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":elasticsearch",
        "//gerrit-reviewdb:client",
        "//gerrit-server:server",
        "//lib:gson",
        "//lib:truth",
        "//lib/elasticsearch",
        "//lib/jgit/org.eclipse.jgit:jgit",
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
        "//gerrit-server:testutil",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)
