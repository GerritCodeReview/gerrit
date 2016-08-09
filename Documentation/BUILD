
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
