load(":rules.bzl", "compile_ts", "polygerrit_bundle")
load("//tools/js:eslint.bzl", "eslint")

package(default_visibility = ["//visibility:public"])

# This list must be in sync with the "include" list in the tsconfig.json file
src_dirs = [
    "constants",
    "elements",
    "embed",
    "gr-diff",
    "mixins",
    "samples",
    "scripts",
    "services",
    "styles",
    "types",
    "utils",
]

compiled_pg_srcs = compile_ts(
    name = "compile_pg",
    srcs = glob(
        [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
            ".js",
            ".ts",
        ]],
        exclude = [
            "**/*_test.js",
        ],
    ),
    # The same outdir also appears in the following files:
    # polylint_test.sh
    ts_outdir = "_pg_ts_out",
)

compiled_pg_srcs_with_tests = compile_ts(
    name = "compile_pg_with_tests",
    srcs = glob(
        [
            "**/*.js",
            "**/*.ts",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
            "template_test_srcs/**",
            "rollup.config.js",
        ],
    ),
    # The same outdir also appears in the following files:
    # wct_test.sh
    # karma.conf.js
    ts_outdir = "_pg_with_tests_out",
)

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = compiled_pg_srcs,
    outs = ["polygerrit_ui.zip"],
    entry_point = "_pg_ts_out/elements/gr-app.js",
)

polygerrit_bundle(
    name = "polygerrit_components",
    srcs = compiled_pg_srcs,
    outs = ["polygerrit_components.zip"],
    entry_point = "_pg_ts_out/elements/gr-components.js",
)

filegroup(
    name = "eslint_src_code",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
            "**/*.ts",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
        ],
    ) + [
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
        ],
    ) + compiled_pg_srcs_with_tests,
)

# Workaround for https://github.com/bazelbuild/bazel/issues/1305
filegroup(
    name = "test-srcs-fg",
    srcs = [
        "rollup.config.js",
        ":pg_code",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

# Define the eslinter for polygerrit-ui app
# The eslint macro creates 2 rules: lint_test and lint_bin
eslint(
    name = "lint",
    srcs = [":eslint_src_code"],
    config = ".eslintrc-bazel.js",
    data = [
        # The .eslintrc-bazel.js extends the .eslintrc.js config, pass it as a dependency
        ".eslintrc.js",
        ".prettierrc.js",
        ".eslint-ts-resolver.js",
        "tsconfig_eslint.json",
        # tsconfig_eslint.json extends tsconfig.json, pass it as a dependency
        "tsconfig.json",
    ],
    extensions = [
        ".html",
        ".js",
        ".ts",
    ],
    ignore = ".eslintignore",
    plugins = [
        "@npm//eslint-config-google",
        "@npm//eslint-plugin-html",
        "@npm//eslint-plugin-import",
        "@npm//eslint-plugin-jsdoc",
        "@npm//eslint-plugin-prettier",
        "@npm//gts",
    ],
)

filegroup(
    name = "polylint-fg",
    srcs = [
        # Workaround for https://github.com/bazelbuild/bazel/issues/1305
        "@ui_npm//:node_modules",
    ] +
    # Polylinter can't check .ts files, run it on compiled srcs
    compiled_pg_srcs,
)

sh_test(
    name = "polylint_test",
    size = "large",
    srcs = ["polylint_test.sh"],
    args = [
        "$(location @tools_npm//polymer-cli/bin:polymer)",
        "$(location polymer.json)",
    ],
    data = [
        "polymer.json",
        ":polylint-fg",
        "@tools_npm//polymer-cli/bin:polymer",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)
