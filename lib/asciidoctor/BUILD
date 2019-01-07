java_library(
    name = "asciidoctor",
    data = ["//lib:LICENSE-asciidoctor"],
    visibility = ["//java/com/google/gerrit/asciidoctor:__pkg__"],
    exports = [
        "@asciidoctor-api//jar",
        "@asciidoctor//jar",
    ],
    runtime_deps = [":jruby"],
)

java_library(
    name = "jruby",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@jruby//jar"],
)
