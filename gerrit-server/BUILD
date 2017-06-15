load("//lib/prolog:prolog.bzl", "prolog_cafe_library")
load("//tools/bzl:junit.bzl", "junit_tests")

CONSTANTS_SRC = [
    "src/main/java/com/google/gerrit/server/documentation/Constants.java",
]

SRCS = glob(
    ["src/main/java/**/*.java"],
    exclude = CONSTANTS_SRC,
)

RESOURCES = glob(["src/main/resources/**/*"])

java_library(
    name = "constants",
    srcs = CONSTANTS_SRC,
    visibility = ["//visibility:public"],
)

prolog_cafe_library(
    name = "prolog-common",
    srcs = ["src/main/prolog/gerrit_common.pl"],
    visibility = ["//visibility:public"],
    deps = [":server"],
)

java_library(
    name = "server",
    srcs = SRCS,
    resources = RESOURCES,
    visibility = ["//visibility:public"],
    deps = [
        ":constants",
        "//gerrit-antlr:query_exception",
        "//gerrit-antlr:query_parser",
        "//gerrit-common:annotations",
        "//gerrit-common:server",
        "//gerrit-extension-api:api",
        "//gerrit-patch-commonsnet:commons-net",
        "//gerrit-patch-jgit:server",
        "//gerrit-prettify:server",
        "//gerrit-reviewdb:server",
        "//gerrit-util-cli:cli",
        "//gerrit-util-ssl:ssl",
        "//lib:args4j",
        "//lib:automaton",
        "//lib:blame-cache",
        "//lib:grappa",
        "//lib:gson",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:jsch",
        "//lib:juniversalchardet",
        "//lib:mime-util",
        "//lib:pegdown",
        "//lib:protobuf",
        "//lib:servlet-api-3_1",
        "//lib:soy",
        "//lib:tukaani-xz",
        "//lib:velocity",
        "//lib/antlr:java_runtime",
        "//lib/auto:auto-value",
        "//lib/bouncycastle:bcpkix-neverlink",
        "//lib/bouncycastle:bcprov-neverlink",
        "//lib/commons:codec",
        "//lib/commons:compress",
        "//lib/commons:dbcp",
        "//lib/commons:lang",
        "//lib/commons:net",
        "//lib/commons:validator",
        "//lib/dropwizard:dropwizard-core",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jgit/org.eclipse.jgit.archive:jgit-archive",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/joda:joda-time",
        "//lib/jsoup",
        "//lib/log:api",
        "//lib/log:jsonevent-layout",
        "//lib/log:log4j",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core-and-backward-codecs",
        "//lib/lucene:lucene-queryparser",
        "//lib/mime4j:core",
        "//lib/mime4j:dom",
        "//lib/ow2:ow2-asm",
        "//lib/ow2:ow2-asm-tree",
        "//lib/ow2:ow2-asm-util",
        "//lib/prolog:runtime",
    ],
)

TESTUTIL_DEPS = [
    ":server",
    "//gerrit-common:annotations",
    "//gerrit-common:server",
    "//gerrit-cache-h2:cache-h2",
    "//gerrit-extension-api:api",
    "//gerrit-gpg:gpg",
    "//gerrit-lucene:lucene",
    "//gerrit-reviewdb:server",
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
    "src/test/java/com/google/gerrit/testutil/**/*.java",
    "src/test/java/com/google/gerrit/server/project/Util.java",
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
        "//gerrit-pgm:init",
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
    "src/test/java/com/google/gerrit/server/**/*Subject.java",
])

java_library(
    name = "custom-truth-subjects",
    testonly = 1,
    srcs = CUSTOM_TRUTH_SUBJECTS,
    deps = [
        ":server",
        "//gerrit-extension-api:api",
        "//gerrit-test-util:test_util",
        "//lib:truth",
    ],
)

PROLOG_TEST_CASE = [
    "src/test/java/com/google/gerrit/rules/PrologTestCase.java",
]

PROLOG_TESTS = glob(
    ["src/test/java/com/google/gerrit/rules/**/*.java"],
    exclude = PROLOG_TEST_CASE,
)

java_library(
    name = "prolog_test_case",
    testonly = 1,
    srcs = PROLOG_TEST_CASE,
    deps = [
        ":server",
        ":testutil",
        "//gerrit-common:server",
        "//gerrit-extension-api:api",
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
    resources = glob(["src/test/resources/com/google/gerrit/rules/**/*"]),
    deps = TESTUTIL_DEPS + [
        ":prolog-common",
        ":prolog_test_case",
        ":testutil",
        "//lib/prolog:runtime",
    ],
)

QUERY_TESTS = glob(
    ["src/test/java/com/google/gerrit/server/query/**/*.java"],
)

java_library(
    name = "query_tests_code",
    testonly = 1,
    srcs = QUERY_TESTS,
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":prolog-common",
        ":testutil",
        "//gerrit-antlr:query_exception",
        "//gerrit-antlr:query_parser",
        "//lib/antlr:java_runtime",
    ],
)

junit_tests(
    name = "query_tests",
    size = "large",
    srcs = QUERY_TESTS,
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":prolog-common",
        ":testutil",
        "//gerrit-antlr:query_exception",
        "//gerrit-antlr:query_parser",
        "//lib/antlr:java_runtime",
    ],
)

junit_tests(
    name = "server_tests",
    size = "large",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = TESTUTIL + CUSTOM_TRUTH_SUBJECTS + PROLOG_TESTS + PROLOG_TEST_CASE + QUERY_TESTS,
    ),
    resources = glob(["src/test/resources/com/google/gerrit/server/**/*"]),
    visibility = ["//visibility:public"],
    deps = TESTUTIL_DEPS + [
        ":custom-truth-subjects",
        ":prolog-common",
        ":testutil",
        "//gerrit-antlr:query_exception",
        "//gerrit-patch-jgit:server",
        "//gerrit-test-util:test_util",
        "//lib:args4j",
        "//lib:grappa",
        "//lib:gson",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:protobuf",
        "//lib:truth-java8-extension",
        "//lib/bouncycastle:bcprov",
        "//lib/bouncycastle:bcpkix",
        "//lib/dropwizard:dropwizard-core",
        "//lib/guice:guice-assistedinject",
        "//lib/prolog:runtime",
        "//lib/commons:codec",
    ],
)

junit_tests(
    name = "testutil_test",
    size = "small",
    srcs = [
        "src/test/java/com/google/gerrit/testutil/IndexVersionsTest.java",
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
