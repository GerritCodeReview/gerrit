load("@npm//@bazel/typescript:index.bzl", "ts_library")

package(default_visibility = ["//visibility:public"])

ts_library(
    name = "utils",
    srcs = glob(["*.ts"]),
    node_modules = "@tools_npm//:node_modules",
    tsconfig = "tsconfig.json",
    deps = [
        "@tools_npm//:node_modules",
    ],
)
