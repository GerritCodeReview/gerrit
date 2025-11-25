load("@rules_shell//shell:sh_test.bzl", "sh_test")

filegroup(
    name = "server",
    srcs = glob(
        ["**/*"],
        exclude = ["BUILD"],
    ),
    visibility = ["//visibility:public"],
)

sh_test(
    name = "commit-msg_test",
    srcs = ["commit-msg_test.sh"],
    data = [":server"],
)
