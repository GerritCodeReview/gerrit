load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")

ABSTRACT_QUERY_TEST = ["AbstractQueryAccountsTest.java"]

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
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/errorprone:annotations",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
    ],
)

junit_tests(
    name = "lucene_query_test",
    size = "large",
    srcs = glob(
        ["LuceneQueryAccountsTest.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":abstract_query_tests",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/guice",
    ],
)

junit_tests(
    name = "fake_query_test",
    size = "large",
    srcs = glob(
        ["FakeQueryAccountsTest.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":abstract_query_tests",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/guice",
    ],
)

junit_tests(
    name = "internal_query_test",
    size = "large",
    srcs = glob(
        ["InternalAccountQueryTest.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":abstract_query_tests",
        "//java/com/google/gerrit/acceptance/config",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/guice",
        "//lib/truth",
    ],
)
