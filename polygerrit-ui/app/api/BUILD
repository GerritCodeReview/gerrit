load("//tools/bzl:js.bzl", "compile_plugin_ts")
load("@build_bazel_rules_nodejs//:index.bzl", "pkg_npm")

package(default_visibility = ["//visibility:public"])

exports_files(glob(["**/*.ts"]))

filegroup(
    name = "js_plugin_api_srcs",
    srcs = glob(["**/*.ts"]),
)

js_plugin_api_compiled = compile_plugin_ts(
    name = "js_plugin_api_compile",
    srcs = glob(["**/*.ts"]),
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
    deps = js_plugin_api_compiled,
)
