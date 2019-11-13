load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")

ABSTRACT_QUERY_TEST = [
    "AbstractQueryChangesTest.java",
    "LuceneQueryChangesTest.java",
]

java_library(
    name = "abstract_query_tests",
    testonly = True,
    srcs = ABSTRACT_QUERY_TEST,
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//java/com/google/gerrit/lucene",
        "//prolog:gerrit-prolog-common",
    ],
    deps = [
        "//java/com/google/gerrit/acceptance/config",
        "//java/com/google/gerrit/acceptance/testsuite/project",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/guice",
        "//lib/truth",
    ],
)

LUCENE_QUERY_TEST = [
    "LuceneQueryChangesLatestIndexVersionTest.java",
    "LuceneQueryChangesPreviousIndexVersionTest.java",
]

[junit_tests(
    name = f[:f.index(".")],
    size = "large",
    srcs = [f],
    visibility = ["//visibility:public"],
    deps = [
        ":abstract_query_tests",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/guice",
        "//lib/truth",
    ],
) for f in LUCENE_QUERY_TEST]

junit_tests(
    name = "small_tests",
    size = "small",
    srcs = glob(
        ["*.java"],
        exclude = ABSTRACT_QUERY_TEST + LUCENE_QUERY_TEST,
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/proto/testing",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/testing",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
    ],
)
