# build highlight.min.js from node modules

load("@aspect_rules_rollup//rollup:defs.bzl", "rollup")

package(
    default_visibility = ["//visibility:public"],
    licenses = ["notice"],
)

# Until https://github.com/highlightjs/highlightjs-GN/pull/4 is merged
# we have to apply the fix manually.
# Once it's merged, this can be removed and we can depend on
# highlightjs-gn.
exports_files(["gn.js"])

rollup(
    name = "highlight.min",
    srcs = [
        ":gn.js",
        "//polygerrit-ui/app:node_modules/highlight.js",
        "//polygerrit-ui/app:node_modules/highlightjs-closure-templates",
        "//polygerrit-ui/app:node_modules/highlightjs-epp",
        "//polygerrit-ui/app:node_modules/highlightjs-structured-text",
        "//polygerrit-ui/app:node_modules/highlightjs-ttcn3",
        "//polygerrit-ui/app:node_modules/highlightjs-vue",
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
