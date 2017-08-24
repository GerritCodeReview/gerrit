load("//tools/bzl:genrule2.bzl", "genrule2")

java_import(
    name = "log4j-config",
    jars = [":log4j-config__jar"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "log4j-config__jar",
    srcs = ["log4j.properties"],
    outs = ["log4j-config.jar"],
    cmd = "cd resources && zip -9Dqr $$ROOT/$@ .",
)
