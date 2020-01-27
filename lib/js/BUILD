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

js_component(
    name = "ba-linkify",
    srcs = ["//lib/ba-linkify:ba-linkify.js"],
    license = "//lib:LICENSE-ba-linkify",
)

##TODO: remove after plugins migration to npm
#load("//tools/bzl:bower_support.bzl", "bower_component")
#
#bower_component(
#    name = "codemirror-minified",
#    license = "//lib:LICENSE-codemirror-minified",
#)
#
#bower_component(
#    name = "resemblejs",
#    license = "//lib:LICENSE-resemblejs",
#)
#End of removal
