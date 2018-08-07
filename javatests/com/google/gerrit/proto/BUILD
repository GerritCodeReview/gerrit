load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "proto_tests",
    srcs = glob(["*.java"]),
    deps = [
        "//lib/truth:truth-proto-extension",
        "//proto:reviewdb_java_proto",

        # TODO(dborowitz): These are already runtime_deps of
        # truth-proto-extension, but either omitting them or adding them as
        # runtime_deps to this target fails with:
        #   class file for com.google.common.collect.Multimap not found
        "//lib:guava",
        "//lib/truth",
    ],
)
