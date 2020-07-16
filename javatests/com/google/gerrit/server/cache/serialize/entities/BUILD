load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/cache/serialize/entities",
        "//java/com/google/gerrit/server/cache/testing",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
        "//proto/testing:test_java_proto",
        "@jgit//org.eclipse.jgit:jgit",
    ],
)
