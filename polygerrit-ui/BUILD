package(
    default_visibility = ["//visibility:public"],
)

load("//tools/bzl:js.bzl", "bower_component_bundle")
load("//tools/bzl:genrule2.bzl", "genrule2")

bower_component_bundle(
    name = "polygerrit_components.bower_components",
    deps = [
        "//lib/js:ba-linkify",
        "//lib/js:es6-promise",
        "//lib/js:fetch",
        # Although highlightjs is inserted separately in the UI zip, it's used
        # by local development servers (e.g. --polygerrit-dev or run-server.sh).
        "//lib/js:highlightjs",
        "//lib/js:iron-a11y-keys-behavior",
        "//lib/js:iron-autogrow-textarea",
        "//lib/js:iron-dropdown",
        "//lib/js:iron-icon",
        "//lib/js:iron-iconset-svg",
        "//lib/js:iron-input",
        "//lib/js:iron-overlay-behavior",
        "//lib/js:iron-selector",
        "//lib/js:moment",
        "//lib/js:page",
        "//lib/js:paper-button",
        "//lib/js:paper-input",
        "//lib/js:paper-item",
        "//lib/js:paper-listbox",
        "//lib/js:paper-tabs",
        "//lib/js:paper-toggle-button",
        "//lib/js:polymer",
        "//lib/js:polymer-resin",
        "//lib/js:promise-polyfill",
    ],
)

genrule2(
    name = "fonts",
    srcs = [
        "//lib/fonts:robotofonts",
    ],
    outs = ["fonts.zip"],
    cmd = " && ".join([
        "mkdir -p $$TMP/fonts",
        "cp $(SRCS) $$TMP/fonts/",
        "cd $$TMP",
        "find fonts/ -exec touch -t 198001010000 '{}' ';'",
        "zip -qr $$ROOT/$@ fonts",
    ]),
    output_to_bindir = 1,
    visibility = ["//visibility:public"],
)
