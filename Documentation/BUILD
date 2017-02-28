package(default_visibility = ["//visibility:public"])

load("//tools/bzl:asciidoc.bzl", "documentation_attributes")
load("//tools/bzl:asciidoc.bzl", "genasciidoc")
load("//tools/bzl:asciidoc.bzl", "genasciidoc_zip")
load("//tools/bzl:license.bzl", "license_map")

exports_files([
    "replace_macros.py",
])

filegroup(
    name = "prettify_files",
    srcs = [
        ":prettify.min.css",
        ":prettify.min.js",
    ],
)

genrule(
    name = "prettify_min_css",
    srcs = ["//gerrit-prettify:src/main/resources/com/google/gerrit/prettify/client/prettify.css"],
    outs = ["prettify.min.css"],
    cmd = "cp $< $@",
)

genrule(
    name = "prettify_min_js",
    srcs = ["//gerrit-prettify:src/main/resources/com/google/gerrit/prettify/client/prettify.js"],
    outs = ["prettify.min.js"],
    cmd = "cp $< $@",
)

filegroup(
    name = "resources",
    srcs = glob([
        "images/*.jpg",
        "images/*.png",
    ]) + [
        ":prettify_files",
        "//:LICENSES.txt",
    ],
    visibility = ["//visibility:public"],
)

license_map(
    name = "licenses",
    opts = ["--asciidoctor"],
    targets = [
        "//gerrit-pgm:pgm",
        "//gerrit-gwtui:ui_module",
        "//polygerrit-ui/app:polygerrit_ui",
    ],
    visibility = ["//visibility:public"],
)

license_map(
    name = "js_licenses",
    targets = [
        "//gerrit-gwtui:ui_module",
        "//polygerrit-ui/app:polygerrit_ui",
    ],
    visibility = ["//visibility:public"],
)

DOC_DIR = "Documentation"

SRCS = glob(["*.txt"]) + [":licenses.txt"]

genrule(
    name = "index",
    srcs = SRCS,
    outs = ["index.jar"],
    cmd = "$(location //lib/asciidoctor:doc_indexer) " +
          "-o $(OUTS) " +
          "--prefix \"%s/\" " % DOC_DIR +
          "--in-ext \".txt\" " +
          "--out-ext \".html\" " +
          "$(SRCS)",
    tools = ["//lib/asciidoctor:doc_indexer"],
)

# For the same srcs, we can have multiple genasciidoc_zip rules, but only one
# genasciidoc rule. Because multiple genasciidoc rules will have conflicting
# output files.
genasciidoc(
    name = "Documentation",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    visibility = ["//visibility:public"],
)

genasciidoc_zip(
    name = "html",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
    visibility = ["//visibility:public"],
)

genasciidoc_zip(
    name = "searchfree",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
    searchbox = False,
    visibility = ["//visibility:public"],
)
