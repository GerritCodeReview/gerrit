load("//tools/bzl:junit.bzl", "junit_tests")

java_library(
    name = "cache-h2",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-common:server",
        "//gerrit-extension-api:api",
        "//gerrit-server:server",
        "//lib:guava",
        "//lib:h2",
        "//lib/guice",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)
