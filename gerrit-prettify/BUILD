load("//tools/bzl:gwt.bzl", "gwt_module")

SRC = "src/main/java/com/google/gerrit/prettify/"

gwt_module(
    name = "client",
    srcs = glob([
        SRC + "common/**/*.java",
    ]),
    exported_deps = [
        "//gerrit-gwtexpui:SafeHtml",
        "//gerrit-patch-jgit:Edit",
        "//gerrit-patch-jgit:client",
        "//gerrit-reviewdb:client",
        "//java/com/google/gerrit/extensions:client",
        "//lib:gwtjsonrpc",
        "//lib:gwtjsonrpc_src",
    ],
    gwt_xml = SRC + "PrettyFormatter.gwt.xml",
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user-neverlink"],
)

java_library(
    name = "server",
    srcs = glob([SRC + "common/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-patch-jgit:server",
        "//gerrit-reviewdb:server",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)

exports_files([
    "src/main/resources/com/google/gerrit/prettify/client/prettify.css",
    "src/main/resources/com/google/gerrit/prettify/client/prettify.js",
])
