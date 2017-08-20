load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "progress",
    srcs = glob(["client/*.java"]),
    gwt_xml = "Progress.gwt.xml",
    resources = ["client/progress.css"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user"],
)
