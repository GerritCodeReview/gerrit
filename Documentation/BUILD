
load("//tools/bzl:asciidoc.bzl", "documentation_attributes")
load("//tools/bzl:asciidoc.bzl", "genasciidoc")
load("//tools/bzl:license.bzl", "license_map")

exports_files([
  "replace_macros.py",
])

PRETTIFY_FILES = [
  "prettify.min.css",
  "prettify.min.js",
]

genrule(
  name = "prettify_files",
  cmd = "cp $(SRCS) $(@D)",
  srcs = [
    "//gerrit-prettify:prettify.min.css",
    "//gerrit-prettify:prettify.min.js",
  ],
  outs = PRETTIFY_FILES,
)

filegroup(
  name = 'resources',
  srcs = glob([
    "images/*.jpg",
    "images/*.png",
  ]),
  data = PRETTIFY_FILES,
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
      "$(SRCS) " +
      "$(location :licenses.txt)",
  tools = [
    ":licenses.txt",
    "//lib/asciidoctor:doc_indexer",
  ],
  srcs = SRCS,
  outs = ["index.jar"],
)

genasciidoc(
  name = "html",
  srcs = SRCS + [":licenses.txt"],
  # TODO(fishywang): implement git_describe() in bazel
  attributes = documentation_attributes("v2.14-dirty"),
  backend = "html5",
  visibility = ["//visibility:public"],
)
