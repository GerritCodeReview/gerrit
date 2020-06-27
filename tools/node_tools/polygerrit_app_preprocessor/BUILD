load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary")
load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("@npm//@bazel/typescript:index.bzl", "ts_library")

package(default_visibility = ["//visibility:public"])

ts_library(
    name = "preprocessor",
    srcs = glob(["*.ts"]),
    node_modules = "@tools_npm//:node_modules",
    tsconfig = "tsconfig.json",
    deps = [
        "//tools/node_tools/utils",
        "@tools_npm//:node_modules",
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

# TODO(dmfilippov): Find a better way to fix it (another workaround or submit a bug to
# Bazel IJ plugin's) authors or to a ts_config rule author).
# The following genrule is a workaround for a bazel intellij plugin's bug.
# According to the documentation, the ts_config_rules section should be added
# to a .bazelproject file if a project uses typescript
# (https://ij.bazel.build/docs/dynamic-languages-typescript.html)
# Unfortunately, this doesn't work. It seems, that the plugin expects some output from
# the ts_config rule, but the rule doesn't produce any output.
# To workaround the issue, the tsconfig_editor genrule was added. The genrule only copies
# input file to the output file, but this is enough to make bazel IJ plugins works.
# So, if you have any problem a typescript editor (import errors, types not found, etc...) -
# try to build this rule from the command line
# (bazel build tools/node_tools/node_modules/licenses:tsconfig_editor) and then sync bazel project
# in intellij.
genrule(
    name = "tsconfig_editor",
    srcs = ["tsconfig.json"],
    outs = ["tsconfig_editor.json"],
    cmd = "cp $< $@",
)
