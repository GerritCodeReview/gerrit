java_binary(
    name = "asciidoc",
    main_class = "com.google.gerrit.asciidoctor.AsciiDoctor",
    visibility = ["//visibility:public"],
    runtime_deps = [":asciidoc_lib"],
)

java_library(
    name = "asciidoc_lib",
    srcs = ["AsciiDoctor.java"],
    visibility = ["//tools/eclipse:__pkg__"],
    deps = [
        "//lib:args4j",
        "//lib:guava",
        "//lib/asciidoctor",
        "//lib/log:api",
        "//lib/log:nop",
    ],
)

java_binary(
    name = "doc_indexer",
    main_class = "com.google.gerrit.asciidoctor.DocIndexer",
    visibility = ["//tools/eclipse:__pkg__"],
    runtime_deps = [":doc_indexer_lib"],
)

java_library(
    name = "doc_indexer_lib",
    srcs = ["DocIndexer.java"],
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
