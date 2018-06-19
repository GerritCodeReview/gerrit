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
    srcs = glob(["src/main/java/com/google/gerrit/elasticsearch/testing/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":elasticsearch",
        "//gerrit-reviewdb:client",
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
    labels = [
        "docker",
        "elastic",
    ],
    deps = [
        ":elasticsearch",
        ":elasticsearch_test_utils",
        "//gerrit-server:query_tests_code",
        "//gerrit-server:server",
        "//gerrit-server:testutil",
        "//lib:truth",
        "//lib/guice",
        "//lib/httpcomponents:httpcore",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/testcontainers",
    ],
)
