load("//tools/bzl:js.bzl", "js_component")

package(default_visibility = ["//visibility:public"])

js_component(
    name = "highlightjs",
    srcs = ["//lib/highlightjs:highlight.min.js"],
    license = "//lib:LICENSE-highlightjs",
)

filegroup(
    name = "highlightjs_files",
    srcs = ["//lib/highlightjs:highlight.min.js"],
    data = ["//lib:LICENSE-highlightjs"],
)
