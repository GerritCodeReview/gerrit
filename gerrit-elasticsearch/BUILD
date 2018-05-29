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
        "//lib/commons:codec",
        "//lib/elasticsearch-rest-client",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/httpcomponents:httpasyncclient",
        "//lib/httpcomponents:httpclient",
        "//lib/httpcomponents:httpcore",
        "//lib/httpcomponents:httpcore-nio",
        "//lib/jackson:jackson-core",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)

load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = glob([
        "src/test/java/**/ElasticTestUtils.java",
        "src/test/java/**/ElasticContainer.java",
    ]),
    visibility = ["//tools/eclipse:__pkg__"],
    deps = [
        ":elasticsearch",
        "//gerrit-index:index",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//lib:gson",
        "//lib:truth",
        "//lib/guice",
        "//lib/httpcomponents:httpcore",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/testcontainers",
    ],
)

junit_tests(
    name = "elasticsearch_tests",
    size = "large",
    srcs = glob(["src/test/java/**/*Test.java"]),
    tags = [
        "docker",
        "elastic",
    ],
    deps = [
        ":elasticsearch",
        ":elasticsearch_test_utils",
        "//gerrit-server:query_tests_code",
        "//gerrit-server:server",
        "//gerrit-server:testutil",
        "//lib/guice",
        "//lib/httpcomponents:httpcore",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/testcontainers",
    ],
)
