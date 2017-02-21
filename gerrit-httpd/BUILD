package(
    default_visibility = ["//visibility:public"],
)

load("//tools/bzl:junit.bzl", "junit_tests")

SRCS = glob(
    ["src/main/java/**/*.java"],
)

RESOURCES = glob(["src/main/resources/**/*"])

java_library(
    name = "httpd",
    srcs = SRCS,
    resources = RESOURCES,
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-antlr:query_exception",
        "//gerrit-common:annotations",
        "//gerrit-common:server",
        "//gerrit-extension-api:api",
        "//gerrit-gwtexpui:linker_server",
        "//gerrit-gwtexpui:server",
        "//gerrit-launcher:launcher",
        "//gerrit-patch-jgit:server",
        "//gerrit-prettify:server",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//gerrit-util-cli:cli",
        "//gerrit-util-http:http",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:jsch",
        "//lib:mime-util",
        "//lib:servlet-api-3_1",
        "//lib/auto:auto-value",
        "//lib/commons:codec",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jgit/org.eclipse.jgit.http.server:jgit-servlet",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
        "//lib/lucene:lucene-core-and-backward-codecs",
    ],
)

