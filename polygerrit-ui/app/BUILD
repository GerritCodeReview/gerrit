load(":rules.bzl", "polygerrit_bundle")
load("//tools/js:eslint.bzl", "eslint")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_test")
load("@npm//@bazel/typescript:index.bzl", "ts_config", "ts_project")

package(default_visibility = ["//visibility:public"])

# This list must be in sync with the "include" list in the follwoing files:
# tsconfig.json, tsconfig_bazel.json, tsconfig_bazel_test.json
src_dirs = [
    "api",
    "constants",
    "elements",
    "embed",
    "gr-diff",
    "mixins",
    "models",
    "scripts",
    "services",
    "styles",
    "types",
    "utils",
    "workers",
]

ts_config(
    name = "ts_config_bazel",
    src = "tsconfig_bazel.json",
    deps = [
        "tsconfig.json",
    ],
)

ts_project(
    name = "compile_pg",
    srcs = glob(
        [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
            ".js",
            ".ts",
        ]],
        exclude = [
            "**/*_test.js",
            "**/*_test.ts",
        ],
    ),
    allow_js = True,
    incremental = True,
    out_dir = "_pg_ts_out",
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":ts_config_bazel",
    deps = [
        "@ui_npm//:node_modules",
    ],
)

ts_config(
    name = "ts_config_bazel_test",
    src = "tsconfig_bazel_test.json",
    deps = [
        "tsconfig.json",
        "tsconfig_bazel.json",
    ],
)

ts_project(
    name = "compile_pg_with_tests",
    srcs = glob(
        [
            "**/*.js",
            "**/*.ts",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
            "rollup.config.js",
        ],
    ),
    allow_js = True,
    incremental = True,
    out_dir = "_pg_with_tests_out",
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":ts_config_bazel_test",
    deps = [
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = [":compile_pg"],
    outs = ["polygerrit_ui.zip"],
    app_name = "gr-app",
    entry_point = "_pg_ts_out/elements/gr-app-entry-point.js",
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
    ) + [":compile_pg_with_tests"],
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
        "@npm//typescript",
    ],
    extensions = [
        ".html",
        ".js",
        ".ts",
    ],
    ignore = ".eslintignore",
    plugins = [
        "@npm//@typescript-eslint/eslint-plugin",
        "@npm//eslint-config-google",
        "@npm//eslint-plugin-html",
        "@npm//eslint-plugin-import",
        "@npm//eslint-plugin-jsdoc",
        "@npm//eslint-plugin-lit",
        "@npm//eslint-plugin-prettier",
        "@npm//eslint-plugin-regex",
        "@npm//gts",
    ],
)

filegroup(
    name = "lit_analysis_src_code",
    srcs = glob(
        ["**/*.ts"],
        exclude = [
            "**/*_html.ts",
            "**/*_test.ts",
        ],
    ) + [
        "@npm//typescript",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

nodejs_test(
    name = "lit_analysis",
    data = [
        ":lit_analysis_src_code",
        "@npm//lit-analyzer",
    ],
    entry_point = "@npm//:node_modules/lit-analyzer/cli.js",
    tags = [
        "local",
        "manual",
    ],
    templated_args = [
        "**/elements/**/*.ts",
        "--strict",
        "--rules.no-property-visibility-mismatch off",
        "--rules.no-incompatible-property-type off",
        "--rules.no-incompatible-type-binding off",
        # TODO: We would actually like to change this to `error`, but we also
        # want to allow certain attributes, for example `aria-description`. This
        # would be possible, if we would run the lit-analyzer as a ts plugin.
        # In tsconfig.json there is an option `globalAttributes` that we could
        # use. But that is not available when running lit-analyzer as cli.
        "--rules.no-unknown-attribute warn",
    ],
)

# app code including tests and tsconfig.json
filegroup(
    name = "web-test-runner_app-sources",
    srcs = glob(
        [
            "**/*.ts",
            "**/*.js",
            "**/tsconfig.json",
        ],
        exclude = ["node_modules/**/*"],
    ),
)
