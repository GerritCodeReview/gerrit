package(
    default_visibility = ["//visibility:public"],
)

load("//tools/bzl:gwt.bzl", "gwt_module")

gwt_module(
    name = "client",
    srcs = glob(["client/**/*.java"]),
    gwt_xml = "ReviewDB.gwt.xml",
    deps = [
        "//java/com/google/gerrit/extensions:client",
        "//lib:gwtorm-client",
        "//lib:gwtorm-client_src",
    ],
)

java_library(
    name = "server",
    srcs = glob(["**/*.java"]),
    resource_strip_prefix = "resources",
    resources = ["//resources/com/google/gerrit/reviewdb"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:gwtorm",
    ],
)
