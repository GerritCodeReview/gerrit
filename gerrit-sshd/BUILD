load("//tools/bzl:junit.bzl", "junit_tests")

SRCS = glob(["src/main/java/**/*.java"])

java_library(
    name = "sshd",
    srcs = SRCS,
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-lucene:lucene",
        "//gerrit-server:receive",
        "//gerrit-server:server",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/util/cli",
        "//java/org/eclipse/jgit:server",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:jsch",
        "//lib:servlet-api-3_1",
        "//lib/auto:auto-value",
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
        "//gerrit-server:server",
        "//java/com/google/gerrit/extensions:api",
        "//lib:truth",
        "//lib/mina:sshd",
    ],
)
