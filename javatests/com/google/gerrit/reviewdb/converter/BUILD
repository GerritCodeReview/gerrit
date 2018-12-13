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
        "//proto:reviewdb_java_proto",
    ],
)

junit_tests(
    name = "compatibility_tests",
    srcs = COMPATIBLITY_TEST_SRCS,
    deps = [
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/reviewdb:server",
        "//lib:guava",
        "//lib:gwtorm-client",
        "//lib:protobuf",
        "//lib/truth",
    ],
)
