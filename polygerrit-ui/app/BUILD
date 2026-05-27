load("@aspect_rules_ts//ts:defs.bzl", "ts_config", "ts_project")
load("@npm//:lit-analyzer/package_json.bzl", lit_analyzer_bin = "bin")
load("@ui_npm//:defs.bzl", "npm_link_all_packages")
load("//tools/js:eslint.bzl", "eslint")
load(":rules.bzl", "polygerrit_bundle")

npm_link_all_packages(name = "node_modules")

package(default_visibility = ["//visibility:public"])

# This list must be in sync with the "include" list in the follwoing files:
# tsconfig.json, tsconfig_bazel.json, tsconfig_bazel_test.json
src_dirs = [
    "api",
    "constants",
    "elements",
    "embed",
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
        allow_empty = True,
        exclude = [
            "**/*_test.js",
            "**/*_test.ts",
        ],
    ),
    allow_js = True,
    incremental = True,
    out_dir = "_pg_ts_out",
    tsconfig = ":ts_config_bazel",
    deps = [
        ":node_modules",
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
            "eslint-bazel.config.js",
            "eslint.config.js",
        ],
    ),
    allow_js = True,
    incremental = True,
    out_dir = "_pg_with_tests_out",
    tsconfig = ":ts_config_bazel_test",
    deps = [
        ":node_modules",
        "//polygerrit-ui:node_modules",
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
        ":node_modules",
        "//polygerrit-ui:node_modules",
    ],
)

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
        ],
        allow_empty = True,
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
        ":node_modules",
        ":pg_code",
        "//polygerrit-ui:node_modules",
    ],
)

# Define the eslinter for polygerrit-ui app
# The eslint macro creates 2 rules: lint_test and lint_bin
eslint(
    name = "lint",
    srcs = [":eslint_src_code"],
    config = "eslint-bazel.config.js",
    data = [
        # The eslint-bazel.config.js extends the eslint.config.js config, pass it as a dependency
        "eslint.config.js",
        ".prettierrc.js",
        ".eslint-ts-resolver.config.js",
        "tsconfig_eslint.json",
        # tsconfig_eslint.json extends tsconfig.json, pass it as a dependency
        "tsconfig.json",
        "//:node_modules/typescript",
    ],
    extensions = [
        ".html",
        ".js",
        ".ts",
    ],
    plugins = [
        "//:node_modules/@typescript-eslint/eslint-plugin",
        "//:node_modules/eslint-config-google",
        "//:node_modules/eslint-plugin-html",
        "//:node_modules/eslint-plugin-import",
        "//:node_modules/eslint-plugin-jsdoc",
        "//:node_modules/eslint-plugin-lit",
        "//:node_modules/eslint-plugin-n",
        "//:node_modules/eslint-plugin-prettier",
        "//:node_modules/eslint-plugin-regex",
        "//:node_modules/eslint-plugin-es-x",
        "//:node_modules/gts",
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
        "//:node_modules/typescript",
        "//polygerrit-ui:node_modules",
    ],
)

lit_analyzer_bin.lit_analyzer_test(
    name = "lit_analysis",
    args = [
        "**/elements/**/*.ts",
        "--strict",
        "--rules.no-property-visibility-mismatch",
        "off",
        "--rules.no-incompatible-property-type",
        "off",
        "--rules.no-incompatible-type-binding",
        "off",
        "--rules.no-unknown-attribute",
        "off",
        "--rules.no-unknown-tag-name",
        "off",
    ],
    chdir = package_name(),
    data = [
        ":lit_analysis_src_code",
    ],
    tags = [
        "local",
        "manual",
    ],
)

# app code including tests, tsconfig.json, and the CSS sheets the
# Web Test Runner HTML loads (main.css, fonts.css, material-icons.css
# under styles/).
filegroup(
    name = "web-test-runner_app-sources",
    srcs = glob(
        [
            "**/*.ts",
            "**/*.js",
            "**/*.css",
            "**/tsconfig.json",
        ],
        exclude = ["node_modules/**/*"],
    ),
)
