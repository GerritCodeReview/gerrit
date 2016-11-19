package(default_visibility = ["//visibility:public"])

java_library(
    name = "elasticsearch",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@elasticsearch//jar"],
    runtime_deps = [
        ":compress-lzf",
        ":hppc",
        ":jna",
        ":jsr166e",
        ":netty",
        ":t-digest",
        "//lib/jackson:jackson-core",
        "//lib/jackson:jackson-dataformat-cbor",
        "//lib/jackson:jackson-dataformat-smile",
        "//lib/joda:joda-time",
        "//lib/lucene:lucene-codecs",
        "//lib/lucene:lucene-highlighter",
        "//lib/lucene:lucene-join",
        "//lib/lucene:lucene-memory",
        "//lib/lucene:lucene-queries",
        "//lib/lucene:lucene-sandbox",
        "//lib/lucene:lucene-spatial",
        "//lib/lucene:lucene-suggest",
    ],
)

# Java REST client for Elasticsearch.
VERSION = "0.1.7"

java_library(
    name = "jest-common",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jest_common//jar"],
)

java_library(
    name = "jest",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jest//jar"],
    runtime_deps = [
        ":elasticsearch",
        ":jest-common",
        "//lib/commons:lang3",
        "//lib/httpcomponents:httpasyncclient",
        "//lib/httpcomponents:httpclient",
        "//lib/httpcomponents:httpcore-nio",
        "//lib/httpcomponents:httpcore-niossl",
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

java_library(
    name = "jna",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jna//jar"],
)
