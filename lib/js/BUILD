package(default_visibility = ["//visibility:public"])

load("//tools/bzl:js.bzl", "bower_component", "js_component")

# For importing new versions of existing bower packages,
#
# 1) edit the versions of 'seed' components in WORKSPACE as desired
#
# 2) Run: 'python tools/js/bower2bazel.py -w lib/js/bower_archives.bzl -b lib/js/bower_components.bzl', to update dependency versions.
#

# For adding a new component as dependency to a bower_component_bundle
#
# 1) add a new bower_archive in WORKSPACE
#
# 2) add bower_component(name="my_new_dependency", seed=True) here
#
# 3) run bower2bazel (see above.)
#
# 4) remove bower_component(name="my_new_dependency", .. ) here
#

load("//lib/js:bower_components.bzl", "define_bower_components")

define_bower_components()

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

bower_component(
    name = "codemirror-minified",
    license = "//lib:LICENSE-codemirror-minified",
)
