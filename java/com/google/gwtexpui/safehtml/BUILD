load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "safehtml",
    srcs = glob(["client/*.java"]),
    gwt_xml = "SafeHtml.gwt.xml",
    resources = ["client/safehtml.css"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user"],
)
