load("//tools/bzl:genrule2.bzl", "genrule2")

genrule2(
    name = "query",
    srcs = ["com/google/gerrit/index/query/Query.g"],
    outs = ["query_antlr.srcjar"],
    cmd = " && ".join([
        "$(location //lib/antlr:antlr-tool) -o $$TMP $<",
        "cd $$TMP",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -q $$ROOT/$@ $$(find . -type f)",
    ]),
    tools = [
        "//lib/antlr:antlr-tool",
    ],
    visibility = ["//visibility:public"],
)
