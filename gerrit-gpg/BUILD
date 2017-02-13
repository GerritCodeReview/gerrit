load("//tools/bzl:junit.bzl", "junit_tests")

DEPS = [
    "//gerrit-common:server",
    "//gerrit-extension-api:api",
    "//gerrit-reviewdb:server",
    "//gerrit-server:server",
    "//lib:guava",
    "//lib:gwtorm",
    "//lib/guice:guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/jgit/org.eclipse.jgit:jgit",
    "//lib/log:api",
]

java_library(
    name = "gpg",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = DEPS + [
        "//lib/bouncycastle:bcpg",
        "//lib/bouncycastle:bcprov",
    ],
)

TESTUTIL_SRCS = glob(["src/test/**/testutil/**/*.java"])

java_library(
    name = "testutil",
    testonly = 1,
    srcs = TESTUTIL_SRCS,
    visibility = ["//visibility:public"],
    deps = DEPS + [
        ":gpg",
        "//lib/bouncycastle:bcpg",
        "//lib/bouncycastle:bcprov",
    ],
)

junit_tests(
    name = "gpg_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = TESTUTIL_SRCS,
    ),
    visibility = ["//visibility:public"],
    deps = DEPS + [
        ":gpg",
        ":testutil",
        "//gerrit-cache-h2:cache-h2",
        "//gerrit-lucene:lucene",
        "//gerrit-server:testutil",
        "//lib:truth",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/bouncycastle:bcpg",
        "//lib/bouncycastle:bcprov",
    ],
)
