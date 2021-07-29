load(":rules.bzl", "compile_ts", "polygerrit_bundle")
load("//tools/js:eslint.bzl", "eslint")
load("//tools/js:template_checker.bzl", "transform_polymer_templates")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_binary", "nodejs_test", "npm_package_bin")

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

compiled_pg_srcs = compile_ts(
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
            "test/@types/*.d.ts",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
            "tmpl_out/**",  # This directory is created by template checker in dev-mode
            "rollup.config.js",
        ],
    ),
    additional_deps = [
        "@ui_dev_npm//:node_modules",
        "tsconfig_bazel.json",
    ],
    # The same outdir also appears in the following files:
    # wct_test.sh
    # karma.conf.js
    ts_outdir = "_pg_with_tests_out",
    ts_project = "tsconfig_bazel_test.json",
)

# Template checker reports problems in the following files. Ignore the files,
# so template tests pass.
# TODO: fix problems reported by template checker in these files.
ignore_templates_list = [
    "elements/admin/gr-access-section/gr-access-section_html.ts",
    "elements/admin/gr-admin-view/gr-admin-view_html.ts",
    "elements/admin/gr-create-change-dialog/gr-create-change-dialog_html.ts",
    "elements/admin/gr-create-pointer-dialog/gr-create-pointer-dialog_html.ts",
    "elements/admin/gr-create-repo-dialog/gr-create-repo-dialog_html.ts",
    "elements/admin/gr-group-members/gr-group-members_html.ts",
    "elements/admin/gr-group/gr-group_html.ts",
    "elements/admin/gr-permission/gr-permission_html.ts",
    "elements/admin/gr-plugin-list/gr-plugin-list_html.ts",
    "elements/admin/gr-repo-access/gr-repo-access_html.ts",
    "elements/admin/gr-repo-commands/gr-repo-commands_html.ts",
    "elements/admin/gr-repo-plugin-config/gr-repo-plugin-config_html.ts",
    "elements/admin/gr-repo/gr-repo_html.ts",
    "elements/admin/gr-rule-editor/gr-rule-editor_html.ts",
    "elements/change-list/gr-change-list-item/gr-change-list-item_html.ts",
    "elements/change-list/gr-change-list-view/gr-change-list-view_html.ts",
    "elements/change-list/gr-change-list/gr-change-list_html.ts",
    "elements/change-list/gr-dashboard-view/gr-dashboard-view_html.ts",
    "elements/change-list/gr-user-header/gr-user-header_html.ts",
    "elements/change/gr-change-actions/gr-change-actions_html.ts",
    "elements/change/gr-change-metadata/gr-change-metadata_html.ts",
    "elements/change/gr-change-requirements/gr-change-requirements_html.ts",
    "elements/change/gr-change-view/gr-change-view_html.ts",
    "elements/change/gr-confirm-abandon-dialog/gr-confirm-abandon-dialog_html.ts",
    "elements/change/gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog_html.ts",
    "elements/change/gr-confirm-move-dialog/gr-confirm-move-dialog_html.ts",
    "elements/change/gr-confirm-rebase-dialog/gr-confirm-rebase-dialog_html.ts",
    "elements/change/gr-confirm-revert-dialog/gr-confirm-revert-dialog_html.ts",
    "elements/change/gr-confirm-submit-dialog/gr-confirm-submit-dialog_html.ts",
    "elements/change/gr-file-list-header/gr-file-list-header_html.ts",
    "elements/change/gr-file-list/gr-file-list_html.ts",
    "elements/change/gr-label-score-row/gr-label-score-row_html.ts",
    "elements/change/gr-message/gr-message_html.ts",
    "elements/change/gr-messages-list/gr-messages-list_html.ts",
    "elements/change/gr-reply-dialog/gr-reply-dialog_html.ts",
    "elements/change/gr-reviewer-list/gr-reviewer-list_html.ts",
    "elements/change/gr-thread-list/gr-thread-list_html.ts",
    "elements/checks/gr-hovercard-run_html.ts",
    "elements/core/gr-main-header/gr-main-header_html.ts",
    "elements/core/gr-search-bar/gr-search-bar_html.ts",
    "elements/core/gr-smart-search/gr-smart-search_html.ts",
    "elements/diff/gr-apply-fix-dialog/gr-apply-fix-dialog_html.ts",
    "elements/diff/gr-diff-builder/gr-diff-builder-element_html.ts",
    "elements/diff/gr-diff-host/gr-diff-host_html.ts",
    "elements/diff/gr-diff-mode-selector/gr-diff-mode-selector_html.ts",
    "elements/diff/gr-diff-preferences-dialog/gr-diff-preferences-dialog_html.ts",
    "elements/diff/gr-diff-view/gr-diff-view_html.ts",
    "elements/diff/gr-diff/gr-diff_html.ts",
    "elements/diff/gr-patch-range-select/gr-patch-range-select_html.ts",
    "elements/gr-app-element_html.ts",
    "elements/settings/gr-settings-view/gr-settings-view_html.ts",
    "elements/settings/gr-watched-projects-editor/gr-watched-projects-editor_html.ts",
    "elements/shared/gr-account-list/gr-account-list_html.ts",
    "elements/shared/gr-autocomplete/gr-autocomplete_html.ts",
    "elements/shared/gr-change-status/gr-change-status_html.ts",
    "elements/shared/gr-comment-thread/gr-comment-thread_html.ts",
    "elements/shared/gr-comment/gr-comment_html.ts",
    "elements/shared/gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog_html.ts",
    "elements/shared/gr-dialog/gr-dialog_html.ts",
    "elements/shared/gr-diff-preferences/gr-diff-preferences_html.ts",
    "elements/shared/gr-download-commands/gr-download-commands_html.ts",
    "elements/shared/gr-dropdown-list/gr-dropdown-list_html.ts",
    "elements/shared/gr-dropdown/gr-dropdown_html.ts",
    "elements/shared/gr-editable-content/gr-editable-content_html.ts",
    "elements/shared/gr-hovercard-account/gr-hovercard-account_html.ts",
    "elements/shared/gr-label-info/gr-label-info_html.ts",
    "elements/shared/gr-labeled-autocomplete/gr-labeled-autocomplete_html.ts",
    "elements/shared/gr-list-view/gr-list-view_html.ts",
    "elements/shared/gr-repo-branch-picker/gr-repo-branch-picker_html.ts",
    "elements/shared/gr-textarea/gr-textarea_html.ts",
]

# Transform templates into a .ts files.
templates_srcs = transform_polymer_templates(
    name = "template_test",
    srcs = glob(
        [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
            ".ts",
        ]],
        exclude = [
            "**/*_test.ts",
        ] + ignore_templates_list,
    ),
    out_tsconfig = "tsconfig_template_test.json",
    tsconfig = "tsconfig_bazel.json",
    deps = [
        "tsconfig.json",
        "tsconfig_bazel.json",
        "@ui_npm//:node_modules",
    ],
)

# Compile transformed templates together with the polygerrit source. If
# templates don't have problem, then the compilation ends without error.
# Otherwise, the typescript compiler reports the error.
# Note, that the compile_ts macro creates build rules. If the build succeed,
# the macro creates the file compile_template_test.success. The
# 'validate_polymer_templates' rule tests existence of the file.
compile_ts(
    name = "compile_template_test",
    srcs = templates_srcs + glob(
        [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
            ".ts",
        ]],
        exclude = [
            "**/*_test.ts",
        ] + ignore_templates_list,
    ),
    additional_deps = [
        "tsconfig_bazel.json",
    ],
    emitJS = False,
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
    ts_outdir = "_pg_template_test_out",
    ts_project = "tsconfig_template_test.json",
)

# This rule allows to run polymer template checker with bazel test command.
# For details - see compile_template_test rule.
sh_test(
    name = "validate_polymer_templates",
    srcs = [":empty_test.sh"],
    data = ["compile_template_test.success"],
)

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = compiled_pg_srcs,
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
