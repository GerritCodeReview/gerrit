load("//tools/bzl:gwt.bzl", "gwt_module")
load("//tools/bzl:junit.bzl", "junit_tests")

SRC = "src/main/java/com/google/gerrit/"

ANNOTATIONS = [
    SRC + x
    for x in [
        "common/Nullable.java",
        "common/audit/Audit.java",
        "common/auth/SignInRequired.java",
    ]
]

java_library(
    name = "annotations",
    srcs = ANNOTATIONS,
    visibility = ["//visibility:public"],
)

gwt_module(
    name = "client",
    srcs = glob([SRC + "common/**/*.java"]),
    exported_deps = [
        "//gerrit-extension-api:api",
        "//gerrit-prettify:client",
        "//lib:guava",
        "//lib:gwtorm-client",
        "//lib:servlet-api-3_1",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/joda:joda-time",
        "//lib/log:api",
    ],
    gwt_xml = SRC + "Common.gwt.xml",
    visibility = ["//visibility:public"],
)

java_library(
    name = "server",
    srcs = glob(
        [SRC + "common/**/*.java"],
        exclude = ANNOTATIONS,
    ),
    visibility = ["//visibility:public"],
    deps = [
        ":annotations",
        "//gerrit-extension-api:api",
        "//gerrit-patch-jgit:server",
        "//gerrit-prettify:server",
        "//gerrit-reviewdb:server",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:servlet-api-3_1",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/joda:joda-time",
        "//lib/log:api",
    ],
)

TEST = "src/test/java/com/google/gerrit/common/"

AUTO_VALUE_TEST_SRCS = [TEST + "AutoValueTest.java"]

junit_tests(
    name = "client_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = AUTO_VALUE_TEST_SRCS,
    ),
    deps = [
        ":client",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
    ],
)

junit_tests(
    name = "auto_value_tests",
    srcs = AUTO_VALUE_TEST_SRCS,
    deps = [
        "//lib:truth",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
