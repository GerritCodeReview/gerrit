# build emojis.min.js from node modules

load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],
)

exports_files(["emojis.js"])

rollup_bundle(
    name = "emojis.min",
    srcs = [
        ":emojis.js",
    ],
    config_file = "rollup.config.js",
    entry_point = "index.js",
    format = "iife",
    rollup_bin = "//tools/node_tools:rollup-bin",
    silent = True,
    sourcemap = "hidden",
    deps = [
        "@tools_npm//rollup",
        "@tools_npm//rollup-plugin-commonjs",
        "@tools_npm//rollup-plugin-node-resolve",
    ],
)
