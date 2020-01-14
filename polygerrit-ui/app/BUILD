load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "bower_component_bundle")
load(":rules.bzl", "polygerrit_bundle")
load("//tools/node_tools/polygerrit_app_preprocessor:index.bzl", "update_links")

package(default_visibility = ["//visibility:public"])

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "node_modules/**",
            "node_modules_licenses/**",
            "bower_components/**",
            "test/**",
            "**/*_test.html",
        ],
    ),
    outs = ["polygerrit_ui.zip"],
    entry_point = "elements/gr-app.html",
    redirects = "redirects.json",
)

bower_component_bundle(
    name = "test_components",
    testonly = True,
    deps = [
        "//lib/js:iron-test-helpers",
        "//lib/js:test-fixture",
        "//lib/js:web-component-tester",
    ],
)

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
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
            "bower_components/**",
            "node_modules/**",
            "node_modules_licenses/**",
            "**/*_test.html",
            "test/**",
            "samples/**",
        ],
    ),
)

genrule2(
    name = "pg_code_zip",
    srcs = [":pg_code"],
    outs = ["pg_code.zip"],
    cmd = " && ".join([
        ("tar -hcf- $(locations :pg_code) |" +
         " tar --strip-components=2 -C $$TMP/ -xf-"),
        "cd $$TMP",
        "TZ=UTC",
        "export TZ",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -rq $$ROOT/$@ *",
    ]),
)

update_links(
    name = "test-srcs-updated-links",
    srcs = [
        "test/common-test-setup.html",
        "test/index.html",
        ":pg_code",
    ],
    redirects = "redirects.json",
)

# Workaround for https://github.com/bazelbuild/bazel/issues/1305
filegroup(
    name = "test-srcs-updated-links-fg",
    srcs = [
        ":test-srcs-updated-links",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

sh_test(
    name = "wct_test",
    size = "enormous",
    srcs = ["wct_test.sh"],
    data = [
        ":test-srcs-updated-links-fg",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

# TODO(taoalpha): alias to `npm run eslint` or just remove once CI moved to npm
sh_test(
    name = "lint_test",
    size = "large",
    srcs = ["lint_test.sh"],
    data = [
        ".eslintrc.json",
        ":pg_code",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

update_links(
    name = "polylint-updated-links",
    srcs = [
        ":pg_code_without_test",
    ],
    redirects = "redirects.json",
)

# Workaround for https://github.com/bazelbuild/bazel/issues/1305
filegroup(
    name = "polylint-updated-links-fg",
    srcs = [
        ":polylint-updated-links",
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
        ":polylint-updated-links-fg",
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
