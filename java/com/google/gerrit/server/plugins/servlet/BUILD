load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_library")

filegroup(
    name = "srcs",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
)

flavoured_library(
    name = "servlet",
    srcs = [":srcs"],
    canonical = "ee11",
    default_flavour = "ee11",
    direction = "to_javax",
    visibility = ["//java/com/google/gerrit/server:__pkg__"],
    deps = [
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib:servlet-api",
        "//lib/guice",
    ],
)
