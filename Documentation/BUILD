
load("//tools/bzl:license.bzl", "license_map")

license_map(
  name = "pgm-licenses",
  targets = ["//gerrit-pgm:pgm"],
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
