load("//tools/bzl:java.bzl", "java_library2")
load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:license.bzl", "license_test")

SRCS = "src/main/java/com/google/gerrit/pgm/"

RSRCS = "src/main/resources/com/google/gerrit/pgm/"

INIT_API_SRCS = glob([SRCS + "init/api/*.java"])

BASE_JETTY_DEPS = [
    "//gerrit-common:server",
    "//gerrit-extension-api:api",
    "//gerrit-gwtexpui:linker_server",
    "//gerrit-gwtexpui:server",
    "//gerrit-httpd:httpd",
    "//gerrit-server:server",
    "//gerrit-sshd:sshd",
    "//lib:guava",
    "//lib/guice:guice",
    "//lib/guice:guice-assistedinject",
    "//lib/guice:guice-servlet",
    "//lib/jgit/org.eclipse.jgit:jgit",
    "//lib/joda:joda-time",
    "//lib/log:api",
    "//lib/log:log4j",
]

DEPS = BASE_JETTY_DEPS + [
    "//gerrit-reviewdb:server",
    "//lib/log:jsonevent-layout",
]

java_library(
    name = "init-api",
    srcs = INIT_API_SRCS,
    visibility = ["//visibility:public"],
    deps = DEPS + ["//gerrit-common:annotations"],
)

java_library(
    name = "init",
    srcs = glob([SRCS + "init/**/*.java"]),
    resources = glob([RSRCS + "init/*"]),
    visibility = ["//visibility:public"],
    deps = DEPS + [
        ":init-api",
        ":util",
        "//gerrit-common:annotations",
        "//gerrit-elasticsearch:elasticsearch",
        "//gerrit-launcher:launcher",  # We want this dep to be provided_deps
        "//gerrit-lucene:lucene",
        "//lib:args4j",
        "//lib:derby",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:h2",
        "//lib/commons:validator",
        "//lib/mina:sshd",
    ],
)

REST_UTIL_DEPS = [
    "//gerrit-cache-h2:cache-h2",
    "//gerrit-util-cli:cli",
    "//lib:args4j",
    "//lib:gwtorm",
    "//lib/commons:dbcp",
]

java_library(
    name = "util",
    visibility = ["//visibility:public"],
    exports = [":util-nodep"],
    runtime_deps = DEPS + REST_UTIL_DEPS,
)

java_library(
    name = "util-nodep",
    srcs = glob([SRCS + "util/*.java"]),
    visibility = ["//visibility:public"],
    deps = DEPS + REST_UTIL_DEPS,  #  We want all these deps to be provided_deps
)

JETTY_DEPS = [
    "//lib/jetty:jmx",
    "//lib/jetty:server",
    "//lib/jetty:servlet",
]

java_library(
    name = "http",
    visibility = ["//visibility:public"],
    exports = [":http-jetty"],
    runtime_deps = DEPS + JETTY_DEPS,
)

java_library(
    name = "http-jetty",
    srcs = glob([SRCS + "http/jetty/*.java"]),
    visibility = ["//visibility:public"],
    deps = JETTY_DEPS + BASE_JETTY_DEPS + [
        # We want all these deps to be provided_deps
        "//gerrit-launcher:launcher",
        "//gerrit-reviewdb:client",
        "//lib:servlet-api-3_1",
    ],
)

REST_PGM_DEPS = [
    ":http",
    ":init",
    ":init-api",
    ":util",
    "//gerrit-cache-h2:cache-h2",
    "//gerrit-elasticsearch:elasticsearch",
    "//gerrit-gpg:gpg",
    "//gerrit-lucene:lucene",
    "//gerrit-oauth:oauth",
    "//gerrit-openid:openid",
    "//lib:args4j",
    "//lib:gwtorm",
    "//lib:protobuf",
    "//lib:servlet-api-3_1-without-neverlink",
    "//lib/prolog:cafeteria",
    "//lib/prolog:compiler",
    "//lib/prolog:runtime",
]

java_library(
    name = "pgm",
    resources = glob([RSRCS + "*"]),
    visibility = ["//visibility:public"],
    runtime_deps = DEPS + REST_PGM_DEPS + [
        ":daemon",
    ],
)

# no transitive deps, used for gerrit-acceptance-framework
java_library(
    name = "daemon",
    srcs = glob([
        SRCS + "*.java",
        SRCS + "rules/*.java",
    ]),
    resources = glob([RSRCS + "*"]),
    visibility = ["//visibility:public"],
    deps = DEPS + REST_PGM_DEPS + [
        # We want all these deps to be provided_deps
        "//gerrit-launcher:launcher",
        "//lib/auto:auto-value",
    ],
)

junit_tests(
    name = "pgm_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":http-jetty",
        ":init",
        ":init-api",
        ":pgm",
        "//gerrit-common:server",
        "//gerrit-server:server",
        "//lib:guava",
        "//lib:junit",
        "//lib:truth",
        "//lib/easymock",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
    ],
)

license_test(
    name = "pgm_license_test",
    target = ":pgm",
)
