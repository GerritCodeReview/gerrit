
load("//tools/bzl:asciidoc.bzl", "documentation_attributes")
load("//tools/bzl:asciidoc.bzl", "genasciidoc")
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
  cmd = "cp $(SRCS) $@",
  outs = ["prettify.min.css"],
)

genrule(
  name = "prettify_min_js",
  srcs = ["//gerrit-prettify:src/main/resources/com/google/gerrit/prettify/client/prettify.js"],
  cmd = "cp $(SRCS) $@",
  outs = ["prettify.min.js"],
)

filegroup(
  name = "resources",
  srcs = glob([
    "images/*.jpg",
    "images/*.png",
  ]),
  data = [":prettify_files"],
  visibility = ['//visibility:public'],
)

license_map(
  name = "licenses",
  targets = [
    "//gerrit-pgm:pgm",
    "//gerrit-gwtui:ui_module",
  ],
  opts = ["--asciidoctor"],
)

DOC_DIR = "Documentation"
SRCS = glob(["*.txt"])

genrule(
  name = "index",
  cmd = "$(location //lib/asciidoctor:doc_indexer) " +
      "-o $(OUTS) " +
      '--prefix "%s/" ' % DOC_DIR +
      '--in-ext ".txt" ' +
      '--out-ext ".html" ' +
      "$(SRCS)",
  tools = [ "//lib/asciidoctor:doc_indexer" ],
  srcs = SRCS + [ ":licenses.txt" ],
  outs = ["index.jar"],
)

genasciidoc(
  name = "html",
  srcs = SRCS + [ ":licenses.txt" ],
  attributes = documentation_attributes(),
  backend = "html5",
  visibility = ["//visibility:public"],
)
