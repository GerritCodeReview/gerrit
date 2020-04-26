load("//tools/bzl:utils.bzl", "template_replacement")

filegroup(
    name = "init",
    srcs = ["gerrit"] + glob(
        ["**/*"],
        exclude = [
            "BUILD",
            "gerrit.sh.tpl",
        ],
    ),
    visibility = ["//visibility:public"],
)

template_replacement(
    name = "gerrit",
    template = "gerrit.sh.tpl",
    version = select({
        "//:java8": "1.8",
        "//:java11": "11",
        "//:java_next": "14",
        "//conditions:default": "1.8",
    }),
)
