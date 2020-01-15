load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary")

package(default_visibility = ["//visibility:public"])

# By default, rollup_bundle rule uses rollup from @npm workspace
# and it expects that all plugins are installed in the same workspace.
# This rule defines another rollup-bin from @tools_npm workspace.
# Usage: rollup_bundle(rollup_bin = "//tools/node_tools:rollup-bin, ...)
nodejs_binary(
    name = "rollup-bin",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/rollup/dist/bin/rollup",
)
