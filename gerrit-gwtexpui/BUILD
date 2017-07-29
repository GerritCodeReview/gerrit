SRC = "src/main/java/com/google/gwtexpui/"

java_library(
    name = "linker_server",
    srcs = glob([SRC + "linker/server/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//lib:servlet-api-3_1"],
)

java_library(
    name = "server",
    srcs = glob([SRC + "server/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//lib:servlet-api-3_1"],
)
