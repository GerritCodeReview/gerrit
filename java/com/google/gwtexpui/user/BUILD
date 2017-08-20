load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "agent",
    srcs = glob(["client/*.java"]),
    gwt_xml = "User.gwt.xml",
    resources = ["client/tooltip.css"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user"],
)
