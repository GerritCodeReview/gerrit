load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/jgit",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize/entities",
        "//java/com/google/gerrit/server/util/time",
        "//lib:guava",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
        "//proto/testing:test_java_proto",
        "@gson//jar",
    ],
)
