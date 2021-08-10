load("@build_bazel_rules_nodejs//:index.bzl", "pkg_npm")
load("@npm//@bazel/typescript:index.bzl", "ts_config", "ts_project")

package(default_visibility = ["//visibility:public"])

exports_files(glob(["**/*.ts"]))

filegroup(
    name = "js_plugin_api_srcs",
    srcs = glob(["**/*.ts"]),
)

ts_config(
    name = "ts_config",
    src = "tsconfig.json",
    deps = [
        "//plugins:plugin-tsconfig",
    ],
)

ts_project(
    name = "js_plugin_api_compiled",
    srcs = glob(["**/*.ts"]),
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":ts_config",
)

# Use this rule for publishing the js plugin api as a package to the npm repo.
pkg_npm(
    name = "js_plugin_api_npm_package",
    srcs = glob(
        ["**/*"],
        exclude = [
            "BUILD",
            "tsconfig.json",
        ],
    ),
    deps = [":js_plugin_api_compiled"],
)
