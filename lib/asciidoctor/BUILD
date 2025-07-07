load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "asciidoctor",
    data = ["//lib:LICENSE-asciidoctor"],
    visibility = ["//java/com/google/gerrit/asciidoctor:__pkg__"],
    exports = ["@external_deps//:org_asciidoctor_asciidoctorj"],
    runtime_deps = [":jruby"],
)

java_library(
    name = "jruby",
    data = ["//lib:LICENSE-DO_NOT_DISTRIBUTE"],
    exports = ["@external_deps//:org_jruby_jruby_complete"],
)
