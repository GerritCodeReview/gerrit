load("@aspect_rules_ts//ts:defs.bzl", "ts_project")

package(default_visibility = ["//visibility:public"])

ts_project(
    name = "utils",
    srcs = glob(["*.ts"]),
    declaration = True,
    tsconfig = "tsconfig.json",
    deps = [
        "//tools/node_tools:node_modules",
    ],
)
