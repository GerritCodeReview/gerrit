load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "jetty",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/lifecycle",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/sshd",
        "//java/com/google/gerrit/util/http",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//lib/guice:guice-servlet",
        "//lib/jetty:jmx",
        "//lib/jetty:server",
        "//lib/jetty:servlet",
        "//lib/log:log4j",
    ],
)
