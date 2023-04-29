load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "auth_tests",
    size = "medium",
    srcs = glob(
        ["**/*.java"],
    ),
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//java/com/google/gerrit/lucene",
        "//lib/bouncycastle:bcprov",
        "//prolog:gerrit-prolog-common",
    ],
    deps = [
        "//java/com/google/gerrit/auth",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/proto/testing",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:guava-retrying",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib:protobuf",
        "//lib/flogger:api",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
    ],
)
