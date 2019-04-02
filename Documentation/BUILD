load("//tools/bzl:asciidoc.bzl", "documentation_attributes", "genasciidoc", "genasciidoc_zip")
load("//tools/bzl:license.bzl", "license_map")

package(default_visibility = ["//visibility:public"])

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
    srcs = ["//resources/com/google/gerrit/prettify:client/prettify.css"],
    outs = ["prettify.min.css"],
    cmd = "cp $< $@",
)

genrule(
    name = "prettify_min_js",
    srcs = ["//resources/com/google/gerrit/prettify:client/prettify.js"],
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
)

license_map(
    name = "licenses",
    opts = ["--asciidoctor"],
    targets = [
        "//polygerrit-ui/app:polygerrit_ui",
        "//java/com/google/gerrit/pgm",
    ],
)

license_map(
    name = "js_licenses",
    targets = [
        "//polygerrit-ui/app:polygerrit_ui",
    ],
)

sh_test(
    name = "check_licenses",
    srcs = ["check_licenses_test.sh"],
    data = [
        "js_licenses.gen.txt",
        "js_licenses.txt",
        "licenses.gen.txt",
        "licenses.txt",
    ],
)

DOC_DIR = "Documentation"

SRCS = glob(["*.txt"])

genrule(
    name = "index",
    srcs = SRCS,
    outs = ["index.jar"],
    cmd = "$(location //java/com/google/gerrit/asciidoctor:doc_indexer) " +
          "-o $(OUTS) " +
          "--prefix \"%s/\" " % DOC_DIR +
          "--in-ext \".txt\" " +
          "--out-ext \".html\" " +
          "$(SRCS)",
    tools = ["//java/com/google/gerrit/asciidoctor:doc_indexer"],
)

# For the same srcs, we can have multiple genasciidoc_zip rules, but only one
# genasciidoc rule. Because multiple genasciidoc rules will have conflicting
# output files.
genasciidoc(
    name = "Documentation",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
)

genasciidoc_zip(
    name = "html",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
)

genasciidoc_zip(
    name = "searchfree",
    srcs = SRCS,
    attributes = documentation_attributes(),
    backend = "html5",
    directory = DOC_DIR,
    searchbox = False,
)
