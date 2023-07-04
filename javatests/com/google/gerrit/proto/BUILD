load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proto_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/testing:gerrit-junit",
        "//lib:protobuf",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:cache_java_proto",
    ],
)
