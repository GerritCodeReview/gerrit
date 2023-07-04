load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gerrit-test-util",
    testonly = True,
    srcs = glob(
        ["**/*.java"],
        exclude = [
            "AssertableExecutorService.java",
            "TestActionRefUpdateContext.java",
            "GerritJUnit.java",
        ],
    ),
    visibility = ["//visibility:public"],
    exports = [
        ":gerrit-junit",
        "//lib:junit",
        "//lib/mockito",
    ],
    runtime_deps = ["//java/com/google/gerrit/index/testing"],
    deps = [
        "//java/com/google/gerrit/acceptance/config",
        "//java/com/google/gerrit/acceptance/testsuite/group",
        "//java/com/google/gerrit/acceptance/testsuite/project",
        "//java/com/google/gerrit/auth",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/gpg",
        "//java/com/google/gerrit/httpd/auth/restapi",
        "//java/com/google/gerrit/index",
        "//java/com/google/gerrit/index/project",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/mail",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server:module",
        "//java/com/google/gerrit/server/api",
        "//java/com/google/gerrit/server/audit",
        "//java/com/google/gerrit/server/cache/h2",
        "//java/com/google/gerrit/server/cache/mem",
        "//java/com/google/gerrit/server/group/testing",
        "//java/com/google/gerrit/server/logging",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/server/schema",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:test-ref-update-context",
        "//lib:guava",
        "//lib:h2",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:junit",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//lib/errorprone:annotations",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/log:impl-log4j",
        "//lib/truth",
    ],
)

java_library(
    name = "gerrit-junit",
    srcs = ["GerritJUnit.java"],
    visibility = ["//visibility:public"],
)

java_library(
    # This can't be part of gerrit-test-util because of https://github.com/google/guava/issues/2837
    name = "assertable-executor",
    testonly = True,
    srcs = ["AssertableExecutorService.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//lib:guava",
        "//lib/truth",
    ],
)

java_library(
    name = "test-ref-update-context",
    testonly = True,
    srcs = ["TestActionRefUpdateContext.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/server",
        "//lib/errorprone:annotations",
    ],
)
