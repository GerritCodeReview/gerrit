load("@build_bazel_rules_nodejs//:defs.bzl", "nodejs_binary")

package(default_visibility = ["//visibility:public"])

nodejs_binary(
    name = "app_preprocessor",
    entry_point = ":app_preprocessor.js",
    node_modules = "@tools_npm//:node_modules",
)

nodejs_binary(
    name = "polymer-bundler",
    entry_point = "@tools_npm//:node_modules/polymer-bundler/lib/bin/polymer-bundler.js",
    node_modules = "@tools_npm//:node_modules",
)

nodejs_binary(
    name = "crisper",
    entry_point = "@tools_npm//:node_modules/crisper/bin/crisper",
    node_modules = "@tools_npm//:node_modules",
)

nodejs_binary(
    name = "rollup",
    entry_point = "@tools_npm//:node_modules/rollup/dist/bin/rollup",
    node_modules = "@tools_npm//:node_modules",
)
