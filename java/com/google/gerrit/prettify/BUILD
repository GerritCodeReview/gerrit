load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "client",
    srcs = glob(["common/**/*.java"]),
    exported_deps = [
        "//java/com/google/gerrit/extensions:client",
        "//java/com/google/gerrit/reviewdb:client",
        "//java/com/google/gwtexpui/safehtml",
        "//java/org/eclipse/jgit:Edit",
        "//java/org/eclipse/jgit:client",
        "//lib:gwtjsonrpc",
        "//lib:gwtjsonrpc_src",
    ],
    gwt_xml = "PrettyFormatter.gwt.xml",
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:user-neverlink"],
)

java_library(
    name = "server",
    srcs = glob(["common/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/reviewdb:server",
        "//java/org/eclipse/jgit:server",
        "//lib:guava",
        "//lib:gwtjsonrpc",
        "//lib/jgit/org.eclipse.jgit:jgit",
    ],
)
