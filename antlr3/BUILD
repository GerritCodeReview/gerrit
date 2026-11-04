load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("//tools/bzl:jar.bzl", "JAR_CREATE", "JAR_SETUP", "JAR_TOOLCHAINS")

genrule2(
    name = "query",
    srcs = ["com/google/gerrit/index/query/Query.g"],
    outs = ["query_antlr.srcjar"],
    cmd = " && ".join([
        JAR_SETUP,
        "$(location //lib/antlr:antlr-tool) -o $$TMP $<",
        "cd $$TMP",
        JAR_CREATE + " --file $$ROOT/$@ $$(find . -type f | LC_ALL=C sort)",
    ]),
    toolchains = JAR_TOOLCHAINS,
    tools = [
        "//lib/antlr:antlr-tool",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "query_parser",
    srcs = [":query"],
    visibility = [
        "//java/com/google/gerrit/index:__subpackages__",
        "//java/com/google/gerrit/server:__subpackages__",
        "//javatests/com/google/gerrit:__subpackages__",
        "//javatests/com/google/gerrit/index:__pkg__",
        "//plugins:__pkg__",
    ],
    deps = [
        "//java/com/google/gerrit/index:query_exception",
        "//lib/antlr:java-runtime",
    ],
)
