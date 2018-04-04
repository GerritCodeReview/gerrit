load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "gwt-base",
    srcs = glob(["**/*.java"]),
    exported_deps = [
        "@checker-compat-qual//jar",
        "@error_prone_annotations//jar",
        "@guava-gwt//jar",
        "@j2objc//jar",
    ],
    gwt_xml = "GwtBase.gwt.xml",
    visibility = ["//visibility:public"],
)
