load("@rules_java//java:defs.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")

proto_library(
    name = "test_proto",
    testonly = 1,
    srcs = ["test.proto"],
)

java_proto_library(
    name = "test_java_proto",
    testonly = 1,
    visibility = ["//visibility:public"],
    deps = [":test_proto"],
)
