load("@npm//@bazel/concatjs:index.bzl", "ts_library")

package(default_visibility = ["//visibility:public"])

ts_library(
    name = "utils",
    srcs = glob(["*.ts"]),
    tsconfig = "tsconfig.json",
    deps = [
        "@tools_npm//:node_modules",
    ],
)
