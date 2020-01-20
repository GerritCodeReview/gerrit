load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary")

package(default_visibility = ["//visibility:public"])

nodejs_binary(
    name = "rollup-bin",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/rollup/dist/bin/rollup",
)
