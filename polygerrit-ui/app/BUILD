load(":rules.bzl", "polygerrit_bundle", "wct_suite", "compile_ts")
load("//tools/js:eslint.bzl", "eslint")

package(default_visibility = ["//visibility:public"])

src_dirs = [
    "behaviors",
    "constants",
    "elements",
    "embed",
    "gr-diff",
    "samples",
    "scripts",
    "services",
    "styles",
    "types",
    "utils",
]

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = glob(
        [src_dir + "/**/*" + ext for src_dir in src_dirs for ext in [
            ".js",
            ".ts",
        ]],
        exclude = [
            "**/*_test.js",
        ],
    ),
    outs = ["polygerrit_ui.zip"],
    entry_point = "elements/gr-app.html",
)

compiled_js_srcs_with_tests = compile_ts(
    name = "compiled_ts_with_tests",
    srcs =glob(
                  [
                      "**/*.js",
                      "**/*.ts",
                  ],
                  exclude = [
                      "node_modules/**",
                      "node_modules_licenses/**",
                      "template_test_srcs/**",
                      "wct.conf.js",
                      "rollup.config.js"
                  ],
              ),
    ts_outdir="ts_test_out",
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
    ) + compiled_js_srcs_with_tests,
)

filegroup(
    name = "pg_code_without_test",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
            "**/*_test.html",
            "test/**",
            "samples/**",
            "**/*_test.js",
        ],
    ),
)

# Workaround for https://github.com/bazelbuild/bazel/issues/1305
filegroup(
    name = "test-srcs-fg",
    srcs = [
        ":pg_code",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

wct_suite(
    name = "wct",
    srcs = [":test-srcs-fg"],
    split_count = 4,
)

# Define the eslinter for polygerrit-ui app
# The eslint macro creates 2 rules: lint_test and lint_bin
eslint(
    name = "lint",
    srcs = [":test-srcs-fg"],
    config = ".eslintrc-bazel.js",
    # The .eslintrc-bazel.js extends the .eslintrc.js config, pass it as a dependency
    data = [".eslintrc.js"],
    extensions = [
        ".html",
        ".js",
    ],
    ignore = ".eslintignore",
    plugins = [
        "@npm//eslint-config-google",
        "@npm//eslint-plugin-html",
        "@npm//eslint-plugin-import",
        "@npm//eslint-plugin-jsdoc",
        "@npm//eslint-plugin-prettier",
    ],
)

# Workaround for https://github.com/bazelbuild/bazel/issues/1305
filegroup(
    name = "polylint-fg",
    srcs = [
        ":pg_code_without_test",
        "@ui_npm//:node_modules",
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

DIRECTORIES = [
    "admin",
    "change",
    "change-list",
    "core",
    "diff",
    "edit",
    "plugins",
    "settings",
    "shared",
    "gr-app",
]

[sh_test(
    name = "template_test_" + directory,
    size = "enormous",
    srcs = ["template_test.sh"],
    args = [directory],
    data = [
        ":pg_code",
        ":template_test_srcs",
    ],
    tags = [
        # Should not run sandboxed.
        "local",
        "template",
    ],
) for directory in DIRECTORIES]

filegroup(
    name = "template_test_srcs",
    srcs = [
        "template_test_srcs/convert_for_template_tests.py",
        "template_test_srcs/template_test.js",
    ],
)
