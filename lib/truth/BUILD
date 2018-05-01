java_library(
    name = "truth",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@truth//jar"],
    runtime_deps = [
        "//lib:guava",
        "//lib:junit",
    ],
)

java_library(
    name = "truth-java8-extension",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = ["@truth-java8-extension//jar"],
    runtime_deps = [
        ":truth",
        "//lib:guava",
    ],
)

java_library(
    name = "truth-liteproto-extension",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:private"],
    exports = ["@truth-liteproto-extension//jar"],
    runtime_deps = [
        ":truth",
        "//lib:guava",
        "//lib:protobuf",
    ],
)

java_library(
    name = "truth-proto-extension",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    visibility = ["//visibility:public"],
    exports = [
        ":truth-liteproto-extension",
        "@truth-proto-extension//jar",
    ],
    runtime_deps = [
        ":truth",
        ":truth-liteproto-extension",
        "//lib:guava",
        "//lib:protobuf",
    ],
)
