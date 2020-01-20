load("@npm_bazel_typescript//:index.bzl", "ts_config", "ts_library")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary", "npm_package_bin")
load("@npm//typescript:index.bzl", "tsc")
load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")

package(default_visibility = ["//visibility:public"])

# Create a tsc_wrapped compiler rule to use in the ts_library
# compiler attribute when using self-managed dependencies
#nodejs_binary(
#    name = "my_tsc",
#    entry_point = "@tools_npm",
#    # Point bazel to your node_modules to find the entry point
#    node_modules = ["//:node_modules"],
#)

ts_library(
    name = "preprocessor",
    srcs = glob(["*.ts"]),
    node_modules = "@tools_npm//:node_modules",
    #supports_workers = False,
    tsconfig = "tsconfig.json",
    deps = [
        #        "@tools_npm//@types/node",
        #        "@tools_npm//@types/parse5",
        #        "@tools_npm//parse5",
        #        "@tools_npm//dom5",
        "@tools_npm//:node_modules",
        "//tools/node_tools/utils",
    ],
)

#rollup_bundle - workaround for https://github.com/bazelbuild/rules_nodejs/issues/1522
rollup_bundle(
    name = "preprocessor-bundle",
    config_file = "rollup.config.js",
    entry_point = "preprocessor.ts",
    format = "cjs",
    rollup_bin = "//tools/node_tools:rollup-bin",
    deps = [
        ":preprocessor",
        "@tools_npm//rollup-plugin-node-resolve",
    ],
)

rollup_bundle(
    name = "links-updater-bundle",
    config_file = "rollup.config.js",
    entry_point = "links-updater.ts",
    format = "cjs",
    rollup_bin = "//tools/node_tools:rollup-bin",
    deps = [
        ":preprocessor",
        "@tools_npm//rollup-plugin-node-resolve",
    ],
)

nodejs_binary(
    name = "preprocessor-bin",
    data = ["@tools_npm//:node_modules"],
    entry_point = "preprocessor-bundle.js",
)

nodejs_binary(
    name = "links-updater-bin",
    data = ["@tools_npm//:node_modules"],
    entry_point = "links-updater-bundle.js",
)

# Workaround for bazel intellij plugin's bug. This rule is referenced by .bazelproject file
genrule(
    name = "tsconfig_editor",
    srcs = ["tsconfig.json"],
    outs = ["tsconfig_editor.json"],
    cmd = "cp $< $@",
)
