load("@build_bazel_rules_nodejs//:index.bzl", "pkg_npm")
load("@npm//@bazel/typescript:index.bzl", "ts_project")

package(default_visibility = ["//visibility:public"])

exports_files(glob(["**/*.ts"]))

filegroup(
    name = "js_plugin_api_srcs",
    srcs = glob(["**/*.ts"]),
)

ts_project(
    name = "js_plugin_api_compiled",
    srcs = glob(["**/*.ts"]),
    tsconfig = "tsconfig.json",
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
