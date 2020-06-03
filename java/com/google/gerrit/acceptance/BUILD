load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:javadoc.bzl", "java_doc")

FUNCTION_SRCS = [
    "testsuite/ThrowingConsumer.java",
    "testsuite/ThrowingFunction.java",
]

DEPLOY_ENV = [
    "//java/com/google/gerrit/exceptions",
    "//java/com/google/gerrit/gpg",
    "//java/com/google/gerrit/git",
    "//java/com/google/gerrit/index:query_exception",
    "//java/com/google/gerrit/launcher",
    "//java/com/google/gerrit/lifecycle",
    "//java/com/google/gerrit/common:annotations",
    "//java/com/google/gerrit/common:server",
    "//java/com/google/gerrit/entities",
    "//java/com/google/gerrit/extensions:api",
    "//java/com/google/gerrit/httpd",
    "//java/com/google/gerrit/index",
    "//java/com/google/gerrit/index/project",
    "//java/com/google/gerrit/json",
    "//java/com/google/gerrit/lucene",
    "//java/com/google/gerrit/mail",
    "//java/com/google/gerrit/metrics",
    "//java/com/google/gerrit/server",
    "//java/com/google/gerrit/server/audit",
    "//java/com/google/gerrit/server/git/receive",
    "//java/com/google/gerrit/server/logging",
    "//java/com/google/gerrit/server/restapi",
    "//java/com/google/gerrit/server/schema",
    "//java/com/google/gerrit/server/util/git",
    "//java/com/google/gerrit/server/util/time",
    "//java/com/google/gerrit/sshd",
    "//lib/auto:auto-value",
    "//lib/auto:auto-value-annotations",
    "//lib:args4j",
    "//lib:gson",
    "//lib:guava-retrying",
    "//lib:jgit",
    "//lib:jgit-ssh-apache",
    "//lib/commons:compress",
    "//lib/commons:lang",
    "//lib/flogger:api",
    "//lib/guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/mail",
    "//lib/mina:sshd",
    "//lib:guava",
    "//lib/bouncycastle:bcpg",
    "//lib/bouncycastle:bcpkix",
    "//lib/bouncycastle:bcprov",
    "//prolog:gerrit-prolog-common",
]

TEST_DEPS = [
    "//java/com/google/gerrit/httpd/auth/openid",
    "//java/com/google/gerrit/pgm",
    "//java/com/google/gerrit/pgm/http/jetty",
    "//java/com/google/gerrit/pgm/util",
    "//java/com/google/gerrit/truth",
    "//java/com/google/gerrit/acceptance/config",
    "//java/com/google/gerrit/acceptance/testsuite/project",
    "//java/com/google/gerrit/server/fixes/testing",
    "//java/com/google/gerrit/server/data",
    "//java/com/google/gerrit/server/group/testing",
    "//java/com/google/gerrit/server/project/testing:project-test-util",
    "//java/com/google/gerrit/testing:gerrit-test-util",
    "//java/com/google/gerrit/extensions/common/testing:common-test-util",
    "//java/com/google/gerrit/extensions/restapi/testing:restapi-test-util",
    "//java/com/google/gerrit/gpg/testing:gpg-test-util",
    "//java/com/google/gerrit/git/testing",
]

PGM_DEPLOY_ENV = [
    "//lib:caffeine",
    "//lib:caffeine-guava",
    "//lib/jackson:jackson-core",
    "//lib/prolog:cafeteria",
]

java_library(
    name = "lib",
    testonly = True,
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/acceptance"],
    visibility = ["//visibility:public"],
    exports = [
        ":framework-lib",
    ] + DEPLOY_ENV + TEST_DEPS,
)

java_binary(
    name = "framework",
    testonly = True,
    deploy_env = [":framework-deploy-env"],
    main_class = "Dummy",
    visibility = ["//visibility:public"],
    runtime_deps = [":framework-lib"],
)

java_binary(
    name = "framework-deploy-env",
    testonly = True,
    main_class = "Dummy",
    runtime_deps = DEPLOY_ENV + PGM_DEPLOY_ENV,
)

exported_deps = [
    ":function",
    "//lib:jgit-junit",
    "//lib:jimfs",
    "//lib:servlet-api",
    "//lib/httpcomponents:fluent-hc",
    "//lib/httpcomponents:httpclient",
    "//lib/httpcomponents:httpcore",
    "//lib/mockito",
    "//lib/truth",
    "//lib/truth:truth-java8-extension",
    "//lib/greenmail",
] + TEST_DEPS

java_library(
    name = "framework-lib",
    testonly = True,
    srcs = glob(
        ["**/*.java"],
        exclude = FUNCTION_SRCS,
    ),
    visibility = ["//visibility:public"],
    exports = exported_deps,
    deps = DEPLOY_ENV + exported_deps,
)

java_library(
    name = "function",
    srcs = FUNCTION_SRCS,
    visibility = ["//visibility:public"],
)

java_doc(
    name = "framework-javadoc",
    testonly = True,
    libs = [":framework-lib"],
    pkgs = ["com.google.gerrit.acceptance"],
    title = "Gerrit Acceptance Test Framework Documentation",
    visibility = ["//visibility:public"],
)
