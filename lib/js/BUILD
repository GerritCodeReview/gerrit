load("//tools/bzl:js.bzl", "js_component")

package(default_visibility = ["//visibility:public"])

js_component(
    name = "highlightjs",
    srcs = ["//lib/highlightjs:highlight.min"],
    license = "//lib:LICENSE-highlightjs",
)

# TODO(dmfilippov) - rename to "highlightjs" after removing js_component
# license-map.py uses rule name to extract package name; everything after
# double underscore are removed.
filegroup(
    name = "highlightjs__files",
    srcs = ["//lib/highlightjs:highlight.min"],
    data = ["//lib:LICENSE-highlightjs"],
)

js_component(
    name = "emojis",
    srcs = ["//lib/emojis:emojis.min"],
    license = "//lib:LICENSE-emojis",
)

filegroup(
    name = "emojis__files",
    srcs = ["//lib/emojis:emojis.min"],
    data = ["//lib:LICENSE-emojis"],
)
