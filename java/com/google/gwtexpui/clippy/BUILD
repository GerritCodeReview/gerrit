load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "clippy",
    srcs = glob(["client/*.java"]),
    data = [
        "//lib:LICENSE-clippy",
        "//lib:LICENSE-silk_icons",
    ],
    gwt_xml = "Clippy.gwt.xml",
    resources = [
        "client/CopyableLabelText.properties",
        "client/clippy.css",
        "client/clippy.swf",
        "client/page_white_copy.png",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gwtexpui/safehtml",
        "//java/com/google/gwtexpui/user:agent",
        "//lib/gwt:user-neverlink",
    ],
)
