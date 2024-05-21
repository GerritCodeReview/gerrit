# build highlight.min.js from node modules

load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],
)

rollup_bundle(
    name = "highlight.min",
    srcs = [
        "@ui_npm//highlight.js",
        "@ui_npm//highlightjs-closure-templates",
        "@ui_npm//highlightjs-epp",
        "@ui_npm//highlightjs-structured-text",
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
