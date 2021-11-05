# build highlight.min.js from node modules

load("@highlightjs_npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("@npm//@bazel/terser:index.bzl", "terser_minified")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],
)

rollup_bundle(
    name = "highlight",
    srcs = [
        "@highlightjs_npm//highlight.js",
        "@highlightjs_npm//highlightjs-closure-templates",
    ],
    config_file = "rollup.config.js",
    entry_point = "index.js",
    format = "cjs",
    silent = True,
    deps = [
        "@highlightjs_npm//@rollup/plugin-commonjs",
        "@highlightjs_npm//@rollup/plugin-json",
        "@highlightjs_npm//@rollup/plugin-node-resolve",
    ],
)

terser_minified(
    name = "highlight.min",
    src = ":highlight",
    config_file = "terser.config.json",
)
