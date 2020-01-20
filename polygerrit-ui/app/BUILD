load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "bower_component_bundle")
load(":rules.bzl", "polygerrit_bundle")
load("@build_bazel_rules_nodejs//:index.bzl", "pkg_web")
load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")
load("//tools/node_tools/polygerrit_app_preprocessor:index.bzl", "prepare_for_bundling", "update_links")
load("//tools/node_tools/legacy:index.bzl", "polymer_bundler_tool")

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
            "bower_components/**",
            "test/**",
            "**/*_test.html",
        ],
    ),
    outs = ["polygerrit_ui.zip"],
    app = "elements/gr-app.html",
)

bower_component_bundle(
    name = "test_components",
    testonly = True,
    deps = [
        "//lib/js:iron-test-helpers",
        "//lib/js:test-fixture",
        "//lib/js:web-component-tester",
        "//polygerrit-ui:polygerrit_components.bower_components",
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

sh_test(
    name = "wct_test",
    size = "enormous",
    srcs = ["wct_test.sh"],
    data = [
        "test/common-test-setup.html",
        "test/index.html",
        ":pg_code.zip",
        ":test_components.zip",
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

sh_test(
    name = "polylint_test",
    size = "large",
    srcs = ["polylint_test.sh"],
    data = [
        "polymer.json",
        ":pg_code_without_test",
        "//polygerrit-ui:polygerrit_components.bower_components.zip",
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
        "//polygerrit-ui:polygerrit_components.bower_components.zip",
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

POLYGERRIT_SOURCE_DIRS = [
    "behaviors",
    "elements",
    "embed",
    "externs",
    "gr-diff",
    "scripts",
    "styles",
    "types",
]

POLYGERRIT_RELEASE_SOURCE = glob(
    include = [dir + "/**/*" + ext for dir in POLYGERRIT_SOURCE_DIRS for ext in [
        ".html",
        ".js",
    ]],
    exclude = [
        "**/*_test.html",
    ],
)

update_links(
    name = "polygerrit-ui-updated-links",
    srcs = POLYGERRIT_RELEASE_SOURCE,
    redirects = "redirects.json",
)

prepare_for_bundling(
    name = "prebundling-srcs",
    srcs = [
        ":polygerrit-ui-updated-links",
    ],
    additional_node_modules_to_preprocess = [
        "@ui_npm//polymer-bridges",
    ],
    entry_point = "elements/gr-app.html",
    node_modules = [
        "@ui_npm//:node_modules",
    ],
    root_path = "polygerrit-ui/app/polygerrit-ui-updated-links/polygerrit-ui/app",
)

rollup_bundle(
    name = "polygerrit-bundle-js",
    srcs = [":prebundling-srcs"],
    config_file = ":rollup.config.js",
    entry_point = "prebundling-srcs/entry.js",
    rollup_bin = "//tools/node_tools:rollup-bin",
    sourcemap = "hidden",
)

polymer_bundler_tool(
    name = "polygerrit-bundle-html",
    srcs = [":prebundling-srcs"],
    entry_point = "prebundling-srcs/entry.html",
)

pkg_web(
    name = "polygerrit-src-pkg",
    srcs = [
        ":polygerrit-ui-updated-links",
        "@ui_npm//:node_modules",
    ],
    additional_root_paths = [
        "ui_npm",
        "polygerrit-ui/app/polygerrit-ui-updated-links/polygerrit-ui/app",
    ],
)
