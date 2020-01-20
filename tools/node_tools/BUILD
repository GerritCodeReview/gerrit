load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary")

package(default_visibility = ["//visibility:public"])

# By default, rollup_bundle rule uses rollup from @npm workspace
# and it expects that all plugins are installed in the same workspace.
# This rule defines another rollup-bin from @tools_npm workspace.
# Usage: rollup_bundle(rollup_bin = "//tools/node_tools:rollup-bin, ...)
nodejs_binary(
    name = "rollup-bin",
    data = [
        "@tools_npm//:node_modules",
    ],
    # The entry point must be "@tools_npm:node_modules/rollup/dist/bin/rollup",
    # But bazel doesn't run it correctly with the following command line:
    # bazel test --test_env=GERRIT_NOTEDB=ON --spawn_strategy=standalone \
    #    --genrule_strategy=standalone --test_output errors --test_summary detailed \
    #    --flaky_test_attempts 3 --test_verbose_timeout_warnings --build_tests_only \
    #    --subcommands //...
    # This command line appears in Gerrit CI.
    entry_point = "@tools_npm//:node_modules/rollup/dist/bin/rollup",
)

# Create a tsc_wrapped compiler rule to use in the ts_library
# compiler attribute when using self-managed dependencies
nodejs_binary(
    name = "tsc_wrapped-bin",
    # Point bazel to your node_modules to find the entry point
    data = ["@tools_npm//:node_modules"],
    entry_point = "@tools_npm//:node_modules/@bazel/typescript/internal/tsc_wrapped/tsc_wrapped.js",
)
