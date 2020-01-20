load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary")

package(default_visibility = ["//visibility:public"])

nodejs_binary(
    name = "polymer-bundler-bin",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/polymer-bundler/lib/bin/polymer-bundler.js",
)

nodejs_binary(
    name = "crisper-bin",
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/crisper/bin/crisper",
)
