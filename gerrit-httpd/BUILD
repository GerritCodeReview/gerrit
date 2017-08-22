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
        "//gerrit-launcher:launcher",
        "//gerrit-server:receive",
        "//gerrit-server:server",
        "//gerrit-util-http:http",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/index:query_exception",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/prettify:server",
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/util/cli",
        "//java/com/google/gwtexpui/linker:server",
        "//java/com/google/gwtexpui/server",
        "//java/org/eclipse/jgit:server",
        "//lib:args4j",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib:gwtorm",
        "//lib:jsch",
        "//lib:mime-util",
        "//lib:servlet-api-3_1",
        "//lib:soy",
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

junit_tests(
    name = "httpd_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        ":httpd",
        "//gerrit-server:server",
        "//gerrit-util-http:http",
        "//gerrit-util-http:testutil",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:gson",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:jimfs",
        "//lib:junit",
        "//lib:servlet-api-3_1-without-neverlink",
        "//lib:soy",
        "//lib:truth",
        "//lib/easymock",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/jgit/org.eclipse.jgit.junit:junit",
        "//lib/joda:joda-time",
    ],
)
