load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

flavoured_library(
    name = "http",
    srcs = [":srcs"],
    deps = [
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
    ],
)
