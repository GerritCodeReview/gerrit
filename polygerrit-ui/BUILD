package(
    default_visibility = ["//visibility:public"],
)

load("//tools/bzl:js.bzl", "bower_component_bundle")
load("//tools/bzl:genrule2.bzl", "genrule2")

bower_component_bundle(
    name = "polygerrit_components.bower_components",
    deps = [
        "//lib/js:es6-promise",
        "//lib/js:fetch",
        # TODO(hanwen): this is inserted separately in the UI zip. Do we need this here?
        "//lib/js:highlightjs",
        "//lib/js:iron-a11y-keys-behavior",
        "//lib/js:iron-autogrow-textarea",
        "//lib/js:iron-dropdown",
        "//lib/js:iron-input",
        "//lib/js:iron-overlay-behavior",
        "//lib/js:iron-selector",
        "//lib/js:moment",
        "//lib/js:page",
        "//lib/js:polymer",
        "//lib/js:polymer-resin",
        "//lib/js:promise-polyfill",
    ],
)

genrule2(
    name = "fonts",
    srcs = [
        "//lib/fonts:sourcecodepro",
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
