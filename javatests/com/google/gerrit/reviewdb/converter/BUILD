load("//tools/bzl:junit.bzl", "junit_tests")

COMPATIBLITY_TEST_SRCS = glob(["*CompatibilityTest.java"])

junit_tests(
    name = "proto_converter_tests",
    srcs = glob(
        ["*.java"],
        exclude = COMPATIBLITY_TEST_SRCS,
    ),
    deps = [
        "//java/com/google/gerrit/proto/testing",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:guava",
        "//lib:protobuf",
        "//lib/truth",
        "//lib/truth:truth-proto-extension",
        "//proto:entities_java_proto",
    ],
)
