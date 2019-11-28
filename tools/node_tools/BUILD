load("@build_bazel_rules_nodejs//:defs.bzl", "nodejs_binary")

exports_files([
    "cwd.js",
    "code_utils.js",
    "links_updater.js",
])

package(default_visibility = ["//visibility:public"])

nodejs_binary(
    name = "app_preprocessor",
    data = [
        ":app_preprocessor.js",
        ":code_utils.js",
        "@tools_npm//:node_modules",
    ],
    entry_point = ":app_preprocessor.js",
)

nodejs_binary(
    name = "polymer-bundler",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/polymer-bundler/lib/bin/polymer-bundler.js",
)

nodejs_binary(
    name = "crisper",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/crisper/bin/crisper",
)

nodejs_binary(
    name = "rollup",
    data = ["@tools_npm//:node_modules", "cwd.js"],
    entry_point = "@tools_npm//:node_modules/rollup/dist/bin/rollup",
)

nodejs_binary(
    name = "polymer-cli",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/polymer-cli/bin/polymer.js",
)

nodejs_binary(
    name = "links_updater",
    data = [
        ":code_utils.js",
        ":links_updater.js",
        "@tools_npm//:node_modules",
    ],
    entry_point = ":links_updater.js",
)
