load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

flavoured_library(
    name = "http",
    srcs = [":srcs"],
    canonical = "ee11",
    default_flavour = "ee11",
    direction = "to_javax",
    deps = [
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
    ],
)
