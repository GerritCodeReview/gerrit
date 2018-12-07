load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proto_converter_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/com/google/gerrit/server/cache/testing",
        "//lib:guava",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:reviewdb_java_proto",
    ],
)
