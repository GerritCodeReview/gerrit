load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proto_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
        "//proto:reviewdb_java_proto",
    ],
)
