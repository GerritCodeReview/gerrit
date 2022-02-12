load("@npm//@bazel/concatjs:index.bzl", "ts_library")

package(default_visibility = ["//visibility:public"])

# TODO: Would be nice to use `ts_project` from @bazel/typescript instead.
# We would prefer to not depend on @bazel/concatjs ...
ts_library(
    name = "utils",
    srcs = glob(["*.ts"]),
    tsconfig = "tsconfig.json",
    deps = [
        "@tools_npm//:node_modules",
    ],
)
