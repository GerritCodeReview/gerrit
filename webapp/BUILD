load("//tools/bzl:genrule2.bzl", "genrule2")

genrule2(
    name = "assets",
    srcs = glob(
        ["**/*"],
        exclude = ["BUILD"],
    ),
    outs = ["assets.zip"],
    cmd = "cd webapp; zip -qr $$ROOT/$@ .",
    visibility = ["//visibility:public"],
)
