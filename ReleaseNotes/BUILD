load("//tools/bzl:asciidoc.bzl", "release_notes_attributes")
load("//tools/bzl:asciidoc.bzl", "genasciidoc")
load("//tools/bzl:asciidoc.bzl", "genasciidoc_zip")

SRCS = glob(["*.txt"])

genasciidoc(
    name = "ReleaseNotes",
    srcs = SRCS,
    attributes = release_notes_attributes(),
    backend = "html5",
    resources = False,
    searchbox = False,
    visibility = ["//visibility:public"],
)

genasciidoc_zip(
    name = "html",
    srcs = SRCS,
    attributes = release_notes_attributes(),
    backend = "html5",
    resources = False,
    searchbox = False,
    visibility = ["//visibility:public"],
)
