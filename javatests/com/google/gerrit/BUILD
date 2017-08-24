load("//tools/bzl:junit.bzl", "junit_tests")

TESTUTIL_DEPS = [
    "//java/com/google/gerrit:module",
    "//java/com/google/gerrit:server",
    "//java/com/google/gerrit/common:annotations",
    "//java/com/google/gerrit/common:server",
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/gpg",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/metrics",
    "//java/com/google/gerrit/reviewdb:server",
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
    "//lib/joda:joda-time",
    "//lib/log:api",
    "//lib/log:impl_log4j",
    "//lib/log:log4j",
]

TESTUTIL = glob([
    "testutil/**/*.java",
    "server/project/Util.java",
])

java_library(
    name = "testutil",
    testonly = 1,
    srcs = TESTUTIL,
    visibility = ["//visibility:public"],
    exports = [
        "//lib/easymock",
        "//lib/powermock:powermock-api-easymock",
        "//lib/powermock:powermock-api-support",
        "//lib/powermock:powermock-core",
        "//lib/powermock:powermock-module-junit4",
        "//lib/powermock:powermock-module-junit4-common",
    ],
    deps = TESTUTIL_DEPS + [
        "//java/com/google/gerrit/pgm/init",
        "//lib/auto:auto-value",
        "//lib/easymock:easymock",
        "//lib/powermock:powermock-api-easymock",
        "//lib/powermock:powermock-api-support",
        "//lib/powermock:powermock-core",
        "//lib/powermock:powermock-module-junit4",
        "//lib/powermock:powermock-module-junit4-common",
    ],
)

CUSTOM_TRUTH_SUBJECTS = glob([
    "server/**/*Subject.java",
])

java_library(
    name = "custom-truth-subjects",
    testonly = 1,
    srcs = CUSTOM_TRUTH_SUBJECTS,
    deps = [
        "//java/com/google/gerrit:server",
        "//java/com/google/gerrit/extensions:api",
        "//javatests/com/google/gerrit/testutil/truth",
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
        ":testutil",
        "//java/com/google/gerrit:server",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
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
        "//prolog:common",
        ":prolog_test_case",
        ":testutil",
        "//lib/prolog:runtime",
    ],
)

QUERY_TESTS = glob(["server/query/**/*.java"])

java_library(
    name = "query_tests_code",
    testonly = 1,
    srcs = QUERY_TESTS,
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        "//prolog:common",
        ":testutil",
    ],
)

junit_tests(
    name = "query_tests",
    size = "large",
    srcs = QUERY_TESTS,
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        "//prolog:common",
        ":testutil",
    ],
)

junit_tests(
    name = "server_tests",
    size = "large",
    srcs = glob(
        ["**/*.java"],
        exclude = TESTUTIL + CUSTOM_TRUTH_SUBJECTS + PROLOG_TESTS + PROLOG_TEST_CASE + QUERY_TESTS,
    ),
    resources = ["//resources/com/google/gerrit:server"],
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":custom-truth-subjects",
        "//prolog:common",
        ":testutil",
        "//java/com/google/gerrit/index:query_exception",
        "//java/org/eclipse/jgit:server",
        "//javatests/com/google/gerrit/testutil/extensions:extensions",
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
    ],
)

junit_tests(
    name = "testutil_test",
    size = "small",
    srcs = [
        "testutil/IndexVersionsTest.java",
    ],
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":testutil",
    ],
)

load("//tools/bzl:javadoc.bzl", "java_doc")

java_doc(
    name = "doc",
    libs = [":server"],
    pkgs = ["com.google.gerrit"],
    title = "Gerrit Review Server Documentation",
)
