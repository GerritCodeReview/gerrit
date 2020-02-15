load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "globalkey",
    srcs = glob(["client/*.java"]),
    gwt_xml = "GlobalKey.gwt.xml",
    resources = [
        "client/KeyConstants.properties",
        "client/key.css",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gwtexpui/safehtml",
        "//lib/gwt:user",
    ],
)
