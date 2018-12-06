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
    name = "reviewdb_proto",
    srcs = [":reviewdb.proto"],
)

java_proto_library(
    name = "reviewdb_java_proto",
    visibility = [
        "//javatests/com/google/gerrit/proto:__pkg__",
        "//tools/eclipse:__pkg__",
    ],
    deps = [":reviewdb_proto"],
)
