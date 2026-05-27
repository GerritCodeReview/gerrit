# build emojis.min.js from node modules

load("@aspect_rules_rollup//rollup:defs.bzl", "rollup")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],
)

exports_files(["emojis.js"])

rollup(
    name = "emojis.min",
    srcs = [
        ":emojis.js",
    ],
    args = [
        "--bundleConfigAsCjs=true",
    ],
    config_file = "rollup.config.js",
    entry_point = "index.js",
    format = "iife",
    node_modules = "//tools/node_tools:node_modules",
    silent = True,
    sourcemap = "hidden",
    deps = [
        "//tools/node_tools:node_modules/@rollup/plugin-commonjs",
        "//tools/node_tools:node_modules/@rollup/plugin-node-resolve",
        "//tools/node_tools:node_modules/@rollup/plugin-terser",
        "//tools/node_tools:node_modules/rollup",
    ],
)
