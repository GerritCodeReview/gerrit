load("@rules_java//java:defs.bzl", "java_import")
load("//tools/bzl:genrule2.bzl", "genrule2")

java_import(
    name = "log4j-config",
    jars = [":log4j-config__jar"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "log4j-config__jar",
    srcs = ["log4j2.properties"],
    outs = ["log4j-config.jar"],
    cmd = "cd $$(dirname $(location log4j.properties)) && zip -9Dqr $$ROOT/$@ .",
)
