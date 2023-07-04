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
        "//java/com/google/gerrit/extensions:api",
        "//proto:cache_java_proto",
        "//proto:rest_java_proto",
    ],
)
