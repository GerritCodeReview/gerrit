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
