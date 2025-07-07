load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proto_converter_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/entities/converter:converters",
        "//java/com/google/gerrit/entities/converter:proto_converter",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/proto/testing",
        "//java/com/google/gerrit/server",
        # We should include flow/converters here, so that they are covered
        # by the SafeProtoConverterTest.
        "//java/com/google/gerrit/server/flow/converters",
        "//lib:guava",
        "//lib:guava-testlib",
        "//lib:jgit",
        "//lib:protobuf",
        "//lib/commons:lang3",
        "//lib/guice",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:entities_java_proto",
    ],
)
