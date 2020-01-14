load("@io_bazel_rules_go//go:def.bzl", "go_binary")
load("//tools/bzl:genrule2.bzl", "genrule2")

package(default_visibility = ["//visibility:public"])

genrule2(
    name = "fonts",
    srcs = [
        "//lib/fonts:robotofonts",
    ],
    outs = ["fonts.zip"],
    cmd = " && ".join([
        "mkdir -p $$TMP/fonts",
        "cp $(SRCS) $$TMP/fonts/",
        "cd $$TMP",
        "find fonts/ -exec touch -t 198001010000 '{}' ';'",
        "zip -qr $$ROOT/$@ fonts",
    ]),
    output_to_bindir = 1,
)

go_binary(
    name = "devserver",
    srcs = ["server.go"],
    data = [
        ":fonts.zip",
        "@ui_npm//:node_modules",
    ],
    deps = [
        "@org_golang_x_tools//godoc/vfs/httpfs:go_default_library",
        "@org_golang_x_tools//godoc/vfs/zipfs:go_default_library",
    ],
)
