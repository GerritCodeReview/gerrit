load("//tools/bzl:junit.bzl", "junit_tests")

TESTUTIL_DEPS = [
    "//java/com/google/gerrit/common:annotations",
    "//java/com/google/gerrit/common:server",
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/gpg",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/metrics",
    "//java/com/google/gerrit/reviewdb:server",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/server:module",
    "//java/com/google/gerrit/server/cache/h2",
    "//java/com/google/gerrit/index",
    "//java/com/google/gerrit/lucene",
    "//lib:gwtorm",
    "//lib:h2",
    "//lib:truth",
    "//lib/guice:guice",
    "//lib/guice:guice-servlet",
    "//lib/jgit/org.eclipse.jgit:jgit",
    "//lib/jgit/org.eclipse.jgit.junit:junit",
    "//lib/log:api",
    "//lib/log:impl_log4j",
    "//lib/log:log4j",
]

CUSTOM_TRUTH_SUBJECTS = glob([
    "**/*Subject.java",
])

java_library(
    name = "custom-truth-subjects",
    testonly = 1,
    srcs = CUSTOM_TRUTH_SUBJECTS,
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/truth",
        "//lib:truth",
    ],
)

PROLOG_TEST_CASE = [
    "rules/PrologTestCase.java",
]

PROLOG_TESTS = glob(
    ["rules/**/*.java"],
    exclude = PROLOG_TEST_CASE,
)

java_library(
    name = "prolog_test_case",
    testonly = 1,
    srcs = PROLOG_TEST_CASE,
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
        "//lib/guice",
        "//lib/prolog:runtime",
    ],
)

junit_tests(
    name = "prolog_tests",
    srcs = PROLOG_TESTS,
    resource_strip_prefix = "prologtests",
    resources = ["//prologtests:gerrit_common_test"],
    deps = TESTUTIL_DEPS + [
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//prolog:gerrit-prolog-common",
        ":prolog_test_case",
        "//lib/prolog:runtime",
    ],
)

ABSTRACT_QUERY_TESTS = glob(
    ["query/**/AbstractQuery*Test.java"],
)

LUCENE_QUERY_TESTS = {i: "query/" + i + "/LuceneQuery" + i.capitalize() + "sTest.java" for i in [
    "account",
    "change",
    "group",
    "project",
]}

java_library(
    name = "abstract_query_tests",
    testonly = 1,
    srcs = ABSTRACT_QUERY_TESTS,
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//prolog:gerrit-prolog-common",
    ],
)

[junit_tests(
    name = "lucene_query_%ss_test" % name,
    size = "large",
    srcs = [src],
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":abstract_query_tests",
        "//java/com/google/gerrit/testing:gerrit-test-util",
    ],
) for name, src in LUCENE_QUERY_TESTS.items()]

junit_tests(
    name = "server_tests",
    size = "large",
    srcs = glob(
        ["**/*.java"],
        exclude = CUSTOM_TRUTH_SUBJECTS + PROLOG_TESTS + PROLOG_TEST_CASE + ABSTRACT_QUERY_TESTS + LUCENE_QUERY_TESTS.values(),
    ),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/server"],
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":custom-truth-subjects",
        "//java/com/google/gerrit/extensions/client/testing:client-test-util",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/server/project/testing:project-test-util",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/org/eclipse/jgit:server",
        "//java/com/google/gerrit/extensions/common/testing:common-test-util",
        "//lib:args4j",
        "//lib:grappa",
        "//lib:gson",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:protobuf",
        "//lib:truth-java8-extension",
        "//lib/bouncycastle:bcprov",
        "//lib/bouncycastle:bcpkix",
        "//lib/guice:guice-assistedinject",
        "//lib/prolog:runtime",
        "//lib/commons:codec",
        "//prolog:gerrit-prolog-common",
    ],
)
