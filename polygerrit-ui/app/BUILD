load(":rules.bzl", "polygerrit_bundle")
load("//tools/js:eslint.bzl", "eslint")

package(default_visibility = ["//visibility:public"])

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = glob(
        [
            "**/*.js",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
            "test/**",
            "**/*_test.html",
            "**/*_test.js",
        ],
    ),
    outs = ["polygerrit_ui.zip"],
    entry_point = "elements/gr-app.html",
)

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
        ],
    ),
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
        "test/common-test-setup.js",
        "test/index.html",
        ":pg_code",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

sh_test(
    name = "wct_test",
    size = "enormous",
    srcs = ["wct_test.sh"],
    args = [
        "$(location @ui_dev_npm//web-component-tester/bin:wct)",
    ],
    data = [
        ":test-srcs-fg",
        "@ui_dev_npm//web-component-tester/bin:wct",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

# Define the eslinter for polygerrit-ui app
# The eslint macro creates 2 rules: lint_test and lint_bin
eslint(
    name="lint",
    plugins=["@npm//eslint-config-google",
             "@npm//eslint-plugin-html",
             "@npm//eslint-plugin-import",
             "@npm//eslint-plugin-jsdoc"],
   extensions=[".html", ".js"],
   config=".eslintrc-bazel.js",
   ignore=".eslintignore",
   srcs=[":test-srcs-fg"],
   # The .eslintrc-bazel.js extends the .eslintrc.js config, pass it as a dependency
   data=[".eslintrc.js"]

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
