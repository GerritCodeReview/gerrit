java_binary(
  name = "asciidoc",
  main_class = "AsciiDoctor",
  runtime_deps = [":asciidoc_lib"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "asciidoc_lib",
  srcs = ["java/AsciiDoctor.java"],
  deps = [
    ":asciidoctor",
    "//lib:args4j",
    "//lib:guava",
    "//lib/log:api",
    "//lib/log:nop",
  ],
  visibility = ["//visibility:public"],
)

java_binary(
  name = "doc_indexer",
  main_class = "DocIndexer",
  runtime_deps = [":doc_indexer_lib"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "doc_indexer_lib",
  srcs = ["java/DocIndexer.java"],
  deps = [
    ":asciidoc_lib",
    "//gerrit-server:constants",
    "//lib:args4j",
    "//lib:guava",
    "//lib/lucene:lucene-analyzers-common",
    "//lib/lucene:lucene-core-and-backward-codecs",
  ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "asciidoctor",
  exports = ["@asciidoctor//jar"],
  runtime_deps = [":jruby"],
  visibility = ["//visibility:public"],
  data = ["//lib:LICENSE-asciidoctor"],
)

java_library(
  name = "jruby",
  exports = ["@jruby//jar"],
  data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
)
