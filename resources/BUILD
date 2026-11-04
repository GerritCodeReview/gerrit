load("@rules_java//java:defs.bzl", "java_import")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("//tools/bzl:jar.bzl", "JAR_CREATE", "JAR_SETUP", "JAR_TOOLCHAINS")

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
        JAR_SETUP,
        "cp $(location log4j.properties) $$TMP/log4j.properties",
        "cd $$TMP",
        JAR_CREATE + " --file $$ROOT/$@ log4j.properties",
    ]),
    toolchains = JAR_TOOLCHAINS,
)
