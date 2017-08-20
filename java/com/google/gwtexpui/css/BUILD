load("//tools/bzl:gwt.bzl", "gwt_module")

java_library(
    name = "css",
    srcs = glob(["rebind/*.java"]),
    resources = ["CSS.gwt.xml"],
    visibility = ["//visibility:public"],
    deps = ["//lib/gwt:dev"],
)
