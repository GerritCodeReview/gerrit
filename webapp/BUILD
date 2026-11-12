load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("//tools/bzl:jar.bzl", "JAR_CREATE", "JAR_SETUP", "JAR_TOOLCHAINS")

genrule2(
    name = "assets",
    srcs = glob(
        ["**/*"],
        exclude = ["BUILD"],
    ),
    outs = ["assets.zip"],
    cmd = " && ".join([
        JAR_SETUP,
        "cd webapp",
        JAR_CREATE + " --file $$ROOT/$@ .",
    ]),
    toolchains = JAR_TOOLCHAINS,
    visibility = ["//visibility:public"],
)

exports_files([
    "diffy.png",
    "gerrit_icon.png",
])
