load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

proto_library(
    name = "cache_proto",
    srcs = ["cache.proto"],
    deps = [":entities_proto"],
)

java_proto_library(
    name = "cache_java_proto",
    visibility = ["//visibility:public"],
    deps = [":cache_proto"],
)

proto_library(
    name = "entities_proto",
    srcs = ["entities.proto"],
)

java_proto_library(
    name = "entities_java_proto",
    visibility = ["//visibility:public"],
    deps = [":entities_proto"],
)

proto_library(
    name = "flow_proto",
    srcs = ["flow.proto"],
)

java_proto_library(
    name = "flow_java_proto",
    visibility = ["//visibility:public"],
    deps = [":flow_proto"],
)
