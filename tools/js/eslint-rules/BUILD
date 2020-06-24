package(default_visibility = ["//visibility:public"])

# To load eslint rules from a directory, we must pass a directory
# name to it. We can't get the directory name in bazel, but we can calculate
# use a file from this directory. We are using README.md for it.
exports_files(["README.md"])

filegroup(
    name = "eslint-rules-srcs",
    srcs = glob(["**/*.js"]),
)
