load(":rules.bzl", "polygerrit_bundle")
load("//tools/js:eslint.bzl", "eslint")
load("//tools/js:template_checker.bzl", "transform_polymer_templates")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary", "nodejs_test", "npm_package_bin")
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
    "samples",
    "scripts",
    "services",
    "styles",
    "types",
    "utils",
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
            "tmpl_out/**",  # This directory is created by template checker in dev-mode
            "rollup.config.js",
        ],
    ),
    allow_js = True,
    incremental = True,
    # The same outdir also appears in the following files:
    # wct_test.sh
    # karma.conf.js
    out_dir = "_pg_with_tests_out",
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":ts_config_bazel_test",
    deps = [
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

# Template checker reports problems in the following files. Ignore the files,
# so template tests pass.
# TODO: fix problems reported by template checker in these files.
ignore_templates_list = [
    "elements/admin/gr-admin-view/gr-admin-view_html.ts",
    "elements/admin/gr-group-members/gr-group-members_html.ts",
    "elements/admin/gr-permission/gr-permission_html.ts",
    "elements/admin/gr-plugin-list/gr-plugin-list_html.ts",
    "elements/admin/gr-repo-access/gr-repo-access_html.ts",
    "elements/admin/gr-repo-commands/gr-repo-commands_html.ts",
    "elements/admin/gr-repo-plugin-config/gr-repo-plugin-config_html.ts",
    "elements/admin/gr-rule-editor/gr-rule-editor_html.ts",
    "elements/change-list/gr-change-list-view/gr-change-list-view_html.ts",
    "elements/change-list/gr-change-list/gr-change-list_html.ts",
    "elements/change-list/gr-dashboard-view/gr-dashboard-view_html.ts",
    "elements/change/gr-change-actions/gr-change-actions_html.ts",
    "elements/change/gr-change-metadata/gr-change-metadata_html.ts",
    "elements/change/gr-change-requirements/gr-change-requirements_html.ts",
    "elements/change/gr-change-view/gr-change-view_html.ts",
    "elements/change/gr-file-list-header/gr-file-list-header_html.ts",
    "elements/change/gr-file-list/gr-file-list_html.ts",
    "elements/change/gr-label-score-row/gr-label-score-row_html.ts",
    "elements/change/gr-message/gr-message_html.ts",
    "elements/change/gr-messages-list/gr-messages-list_html.ts",
    "elements/change/gr-reply-dialog/gr-reply-dialog_html.ts",
    "elements/change/gr-reviewer-list/gr-reviewer-list_html.ts",
    "elements/change/gr-thread-list/gr-thread-list_html.ts",
    "elements/diff/gr-diff-builder/gr-diff-builder-element_html.ts",
    "elements/diff/gr-diff-host/gr-diff-host_html.ts",
    "elements/diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog_html.ts",
    "elements/diff/gr-diff-view/gr-diff-view_html.ts",
    "elements/diff/gr-diff/gr-diff_html.ts",
    "elements/gr-app-element_html.ts",
    "elements/settings/gr-watched-projects-editor/gr-watched-projects-editor_html.ts",
    "elements/shared/gr-account-list/gr-account-list_html.ts",
]

sources_for_template_checking = glob(
    [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
        ".ts",
    ]],
    exclude = [
        "**/*_test.ts",
    ] + ignore_templates_list,
)

# Transform templates into a .ts files.
templates_srcs = transform_polymer_templates(
    name = "template_test",
    srcs = sources_for_template_checking,
    out_tsconfig = "tsconfig_template_test.json",
    tsconfig = "tsconfig_bazel.json",
    deps = [
        "tsconfig.json",
        "tsconfig_bazel.json",
        "@ui_npm//:node_modules",
    ],
)

# After templates are converted into a typescript code, the TS compiler should check that the
# converted code doesn't have the error (i.e. templates don't have problems).
# The input to the compiler is: the converted (i.e. autogenerated) code + original polygerrit code;
# the output (i.e. js code) is not needed (we only care wheather the code has error or not).
# The existing ts_project rule can't compile a mix of a generated and a non-generated code, so it
# can't be used for the purpose of template checking.
# Because the output of TS compiler is not needed, the simplest workaround is to run typescript
# compiler from command line using the sh_test rule. The compiler exits with non-zero return code if
# errors found and sh_test fails.
sh_test(
    name = "polylint_test",
    srcs = [":compile_generated_templates.sh"],
    args = [
        "$(location //tools/node_tools:tsc-bin)",
        "$(location tsconfig_template_test.json)",
    ],
    data = [
        "tsconfig_template_test.json",
        "tsconfig_bazel.json",
        "tsconfig.json",
        "//tools/node_tools:tsc-bin",
        "@ui_npm//:node_modules",
    ] + templates_srcs + sources_for_template_checking,
    tags = [
        "local",
        "manual",
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
    name = "lit_analysis_src_code",
    srcs = glob(
        ["**/*.ts"],
        exclude = [
            "**/*_html.ts",
            "**/*_test.ts",
        ],
    ) + [
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

nodejs_binary(
    name = "lit_analysis",
    data = [
        ":lit_analysis_src_code",
        "@npm//lit-analyzer",
    ],
    entry_point = "@npm//:node_modules/lit-analyzer/cli.js",
    templated_args = [
        "**/elements/**/*.ts",
        "--strict",
        "--rules.no-property-visibility-mismatch off",
        "--rules.no-incompatible-property-type off",
    ],
)
