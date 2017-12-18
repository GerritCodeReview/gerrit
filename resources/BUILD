load("//tools/bzl:genrule2.bzl", "genrule2")

java_import(
    name = "logback-config",
    jars = [":logbacl-config__jar"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "logbacl-config__jar",
    srcs = ["logback.xml"],
    outs = ["logback-config.jar"],
    cmd = "cd resources && zip -9Dqr $$ROOT/$@ .",
)
