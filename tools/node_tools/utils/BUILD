load("@npm_bazel_typescript//:index.bzl", "ts_config", "ts_library")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary", "npm_package_bin")
load("@npm//typescript:index.bzl", "tsc")
load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")

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
