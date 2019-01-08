load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "elasticsearch",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = ["**/testing/**"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-antlr:query_exception",
        "//gerrit-common:annotations",
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

java_library(
    name = "elasticsearch_test_utils",
    testonly = 1,
    srcs = glob(["src/main/java/com/google/gerrit/elasticsearch/testing/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":elasticsearch",
        "//gerrit-server:server",
        "//lib:truth",
        "//lib/guice",
        "//lib/httpcomponents:httpcore",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/testcontainers",
    ],
)

ELASTICSEARCH_DEPS = [
    ":elasticsearch",
    ":elasticsearch_test_utils",
    "//gerrit-server:query_tests_code",
    "//gerrit-server:testutil",
    "//lib:truth",
    "//lib/guice",
    "//lib/jgit/org.eclipse.jgit:jgit",
]

TYPES = [
    "account",
    "change",
    "group",
]

SUFFIX = "sTest.java"

ELASTICSEARCH_TESTS = {i: "src/test/java/com/google/gerrit/elasticsearch/ElasticQuery" + i.capitalize() + SUFFIX for i in TYPES}

ELASTICSEARCH_TESTS_V5 = {i: "src/test/java/com/google/gerrit/elasticsearch/ElasticV5Query" + i.capitalize() + SUFFIX for i in TYPES}

ELASTICSEARCH_TESTS_V6 = {i: "src/test/java/com/google/gerrit/elasticsearch/ElasticV6Query" + i.capitalize() + SUFFIX for i in TYPES}

ELASTICSEARCH_TAGS = [
    "docker",
    "elastic",
    "exclusive",
]

[junit_tests(
    name = "elasticsearch_query_%ss_test" % name,
    size = "large",
    srcs = [src],
    tags = ELASTICSEARCH_TAGS,
    deps = ELASTICSEARCH_DEPS,
) for name, src in ELASTICSEARCH_TESTS.items()]

[junit_tests(
    name = "elasticsearch_query_%ss_test_v5" % name,
    size = "large",
    srcs = [src],
    tags = ELASTICSEARCH_TAGS,
    deps = ELASTICSEARCH_DEPS,
) for name, src in ELASTICSEARCH_TESTS_V5.items()]

[junit_tests(
    name = "elasticsearch_query_%ss_test_v6" % name,
    size = "large",
    srcs = [src],
    tags = ELASTICSEARCH_TAGS,
    deps = ELASTICSEARCH_DEPS,
) for name, src in ELASTICSEARCH_TESTS_V6.items()]

junit_tests(
    name = "elasticsearch_tests",
    size = "small",
    srcs = glob(
        ["src/test/java/com/google/gerrit/elasticsearch/*Test.java"],
        exclude = ["src/test/java/com/google/gerrit/elasticsearch/Elastic*Query*" + SUFFIX],
    ),
    tags = ["elastic"],
    deps = [
        ":elasticsearch",
        ":elasticsearch_test_utils",
        "//gerrit-server:testutil",
        "//lib:guava",
        "//lib:truth",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
