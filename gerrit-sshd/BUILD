load("//tools/bzl:junit.bzl", "junit_tests")

SRCS = glob(["src/main/java/**/*.java"])

java_library(
    name = "sshd",
    srcs = SRCS,
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-cache-h2:cache-h2",
        "//gerrit-cache-mem:mem",
        "//gerrit-common:annotations",
        "//gerrit-common:server",
        "//gerrit-extension-api:api",
        "//gerrit-lucene:lucene",
        "//gerrit-patch-jgit:server",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//gerrit-util-cli:cli",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:jsch",
        "//lib:servlet-api-3_1",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/bouncycastle:bcprov-neverlink",
        "//lib/commons:codec",
        "//lib/dropwizard:dropwizard-core",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",  # SSH should not depend on servlet
        "//lib/jgit/org.eclipse.jgit.archive:jgit-archive",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
        "//lib/log:log4j",
        "//lib/mina:core",
        "//lib/mina:sshd",
    ],
)

junit_tests(
    name = "sshd_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
    ),
    deps = [
        ":sshd",
        "//gerrit-extension-api:api",
        "//gerrit-server:server",
        "//lib:truth",
        "//lib/mina:sshd",
    ],
)
