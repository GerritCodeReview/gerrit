load("@rules_java//java:defs.bzl", "java_import")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")

java_import(
    name = "log4j-config",
    jars = [":log4j-config__jar"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "log4j-config__jar",
    srcs = ["log4j.properties"],
    outs = ["log4j-config.jar"],
    cmd = " && ".join([
        "cp $(location log4j.properties) $$TMP/log4j.properties",
        "cd $$TMP",
        "TZ=UTC touch -t 198001010000 log4j.properties",
        "TZ=UTC zip -X9q $$ROOT/$@ log4j.properties",
    ]),
)
