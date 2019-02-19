proto_library(
    name = "cache_proto",
    srcs = ["cache.proto"],
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
