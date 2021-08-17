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
    # The same outdir also appears in the following files:
    # polylint_test.sh
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
            "test/@types/*.d.ts",
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
    "elements/admin/gr-access-section/gr-access-section_html.ts",
    "elements/admin/gr-admin-view/gr-admin-view_html.ts",
    "elements/admin/gr-create-change-dialog/gr-create-change-dialog_html.ts",
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
    "elements/change/gr-change-actions/gr-change-actions_html.ts",
    "elements/change/gr-change-metadata/gr-change-metadata_html.ts",
    "elements/change/gr-change-requirements/gr-change-requirements_html.ts",
    "elements/change/gr-change-view/gr-change-view_html.ts",
    "elements/change/gr-confirm-abandon-dialog/gr-confirm-abandon-dialog_html.ts",
    "elements/change/gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog_html.ts",
    "elements/change/gr-confirm-move-dialog/gr-confirm-move-dialog_html.ts",
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
    "elements/diff/gr-patch-range-select/gr-patch-range-select_html.ts",
    "elements/gr-app-element_html.ts",
    "elements/settings/gr-watched-projects-editor/gr-watched-projects-editor_html.ts",
    "elements/shared/gr-account-list/gr-account-list_html.ts",
    "elements/shared/gr-comment-thread/gr-comment-thread_html.ts",
    "elements/shared/gr-comment/gr-comment_html.ts",
    "elements/shared/gr-dropdown-list/gr-dropdown-list_html.ts",
    "elements/shared/gr-hovercard-account/gr-hovercard-account_html.ts",
    "elements/shared/gr-label-info/gr-label-info_html.ts",
    "elements/shared/gr-list-view/gr-list-view_html.ts",
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
#
# TODO: Re-instantiate this rule. It broke when switching to ts_project with
# ERROR: //polygerrit-ui/app:compile_template_test srcs cannot be a mix of
#        generated files and source files since this would prevent giving a
#        single rootDir to the TypeScript compiler
# Also, the emitJS feature of compile_ts has to be re-created in some form.
#ts_project(
#    name = "compile_template_test",
#    srcs = templates_srcs + glob(
#        [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
#            ".ts",
#        ]],
#        exclude = [
#            "**/*_test.ts",
#        ] + ignore_templates_list,
#    ),
#    allow_js = True,
#    out_dir = "_pg_template_test_out",
#    # Should not run sandboxed.
#    tags = [
#        "local",
#        "manual",
#    ],
#    tsc = "//tools/node_tools:tsc-bin",
#    tsconfig = "tsconfig_template_test.json",
#)
#
# This rule allows to run polymer template checker with bazel test command.
# For details - see compile_template_test rule.
#
# TODO: Re-instantiate this test. It broke when switching
#       'compile_template_test'to ts_project, see above. ts_project does not
#       create '.success' files.
#sh_test(
#    name = "validate_polymer_templates",
#    srcs = [":empty_test.sh"],
#    data = ["compile_template_test.success"],
#)

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
    name = "polylint-fg",
    srcs = [
        # Workaround for https://github.com/bazelbuild/bazel/issues/1305
        "@ui_npm//:node_modules",
        # Polylinter can't check .ts files, run it on compiled srcs
        ":compile_pg",
    ],
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
