# build highlight.min.js build from node modules

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
    deps = [
        "@highlightjs_npm//@rollup/plugin-commonjs",
        "@highlightjs_npm//@rollup/plugin-json",
        "@highlightjs_npm//@rollup/plugin-node-resolve",
    ],
    entry_point = "index.js",
    config_file = "rollup.config.js",
    format = "cjs",
    silent = True,
)

terser_minified(
    name = "highlight.min",
    src = "highlight",
    config_file = "terser.config.json",
)
