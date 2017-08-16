load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "http",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//lib:servlet-api-3_1"],
)

TESTUTIL_SRCS = glob(["src/test/**/testutil/**/*.java"])

java_library(
    name = "testutil",
    testonly = 1,
    srcs = TESTUTIL_SRCS,
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:servlet-api-3_1",
        "//lib/httpcomponents:httpclient",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

junit_tests(
    name = "http_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = TESTUTIL_SRCS,
    ),
    deps = [
        ":http",
        ":testutil",
        "//lib:junit",
        "//lib:servlet-api-3_1-without-neverlink",
        "//lib:truth",
        "//lib/easymock",
    ],
)
