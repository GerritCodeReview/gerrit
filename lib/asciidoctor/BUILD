java_binary(
    name = "asciidoc",
    main_class = "AsciiDoctor",
    visibility = ["//visibility:public"],
    runtime_deps = [":asciidoc_lib"],
)

java_library(
    name = "asciidoc_lib",
    srcs = ["java/AsciiDoctor.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":asciidoctor",
        "//lib:args4j",
        "//lib:guava",
        "//lib/log:api",
        "//lib/log:nop",
    ],
)

java_binary(
    name = "doc_indexer",
    main_class = "DocIndexer",
    visibility = ["//visibility:public"],
    runtime_deps = [":doc_indexer_lib"],
)

java_library(
    name = "doc_indexer_lib",
    srcs = ["java/DocIndexer.java"],
    visibility = ["//visibility:public"],
    deps = [
        ":asciidoc_lib",
        "//java/com/google/gerrit/server:constants",
        "//lib:args4j",
        "//lib:guava",
        "//lib/lucene:lucene-analyzers-common",
        "//lib/lucene:lucene-core-and-backward-codecs",
    ],
)

java_library(
    name = "asciidoctor",
    data = ["//lib:LICENSE-asciidoctor"],
    visibility = ["//visibility:public"],
    exports = ["@asciidoctor//jar"],
    runtime_deps = [":jruby"],
)

java_library(
    name = "jruby",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@jruby//jar"],
)
