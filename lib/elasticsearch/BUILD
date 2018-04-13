package(default_visibility = ["//visibility:public"])

java_library(
    name = "elasticsearch",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@elasticsearch//jar"],
    runtime_deps = [
        ":compress-lzf",
        ":hppc",
        ":jsr166e",
        ":netty",
        ":t-digest",
        "//lib/jackson:jackson-core",
        "//lib/jackson:jackson-dataformat-cbor",
        "//lib/lucene:lucene-highlighter",
        "//lib/lucene:lucene-join",
        "//lib/lucene:lucene-memory",
        "//lib/lucene:lucene-queries",
        "//lib/lucene:lucene-spatial",
        "//lib/lucene:lucene-suggest",
    ],
)

java_library(
    name = "compress-lzf",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//lib/elasticsearch:__pkg__"],
    exports = ["@compress_lzf//jar"],
)

java_library(
    name = "hppc",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//lib/elasticsearch:__pkg__"],
    exports = ["@hppc//jar"],
)

java_library(
    name = "jsr166e",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//lib/elasticsearch:__pkg__"],
    exports = ["@jsr166e//jar"],
)

java_library(
    name = "netty",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//lib/elasticsearch:__pkg__"],
    exports = ["@netty//jar"],
)

java_library(
    name = "t-digest",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//lib/elasticsearch:__pkg__"],
    exports = ["@t_digest//jar"],
)
