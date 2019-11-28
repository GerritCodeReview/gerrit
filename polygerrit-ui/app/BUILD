load("//tools/bzl:genrule2.bzl", "genrule2")
load(":rules.bzl", "polygerrit_bundle")
load("@build_bazel_rules_nodejs//:defs.bzl", "nodejs_test")

#load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "pg_release_code_files",
    srcs = glob(
        [
            "behaviors/**/*.html",
            "behaviors/**/*.js",
            "elements/**/*.html",
            "elements/**/*.js",
            "polymer_legacy_support/**/*.html",
            "polymer_legacy_support/**/*.js",
            "scripts/**/*.html",
            "scripts/**/*.js",
            "styles/**/*.html",
            "styles/**/*.js",
            "types/**/*.html",
            "types/**/*.js",
        ],
        exclude = [
            "**/*_test.html",
        ],
    ),
)

filegroup(
    name = "pg_test_code_files",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "node_modules/**",
        ],
    ),
)

filegroup(
    name = "src_for_build",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
            "redirects.json",
            "rollup.config.js",
        ],
        exclude = [
            "test/**",
            "**/*_test.html",
            "html_to_js.js",
            "node_modules/**",
        ],
    ),
)

genrule(
    name = "polygerrit_src_bundle",
    srcs = [
        ":src_for_build",
        "@ui_npm//:node_modules",
    ],
    outs = [
        "bundle/gr-app.html",
        "bundle/gr-app.js",
    ],
    cmd = " && ".join([
        "mkdir -p $(@D)/out/updated_links",
        "cp -r -L polygerrit-ui $(@D)/out/updated_links",
        "$(location //tools/node_tools:links_updater) $(@D)/out/updated_links/polygerrit-ui $(@D)/out/updated_links/polygerrit-ui app app/redirects.json",
        "ln -s external/ui_npm/node_modules $(@D)/out/updated_links/polygerrit-ui/app/node_modules",
        "$(location //tools/node_tools:app_preprocessor) $(@D)/out/updated_links/polygerrit-ui/app $(@D)/out/preprocessed elements/gr-app.html",
        "ls -l $(@D)/out/preprocessed/",
        "mkdir $(@D)/out/bundle",
        "$(location //tools/node_tools:polymer-bundler) --root $(@D)/out/preprocessed --out-file $(@D)/out/bundle/gr-app.html elements/gr-app.html",
        "$(location //tools/node_tools:crisper) -s $(@D)/out/bundle/gr-app.html -h $(@D)/bundle/gr-app.html -j $(@D)/bundle/gr-app.js --always-write-script",
        "$(location //tools/node_tools:rollup) -c $(@D)/out/preprocessed/rollup.config.js --input $(@D)/out/preprocessed/elements/gr-app.html_generated.js --file $(@D)/bundle/gr-app.js",
        "rm -rf $(@D)/out",
    ]),
    tools = [
        "//tools/node_tools:app_preprocessor",
        "//tools/node_tools:crisper",
        "//tools/node_tools:links_updater",
        "//tools/node_tools:polymer-bundler",
        "//tools/node_tools:rollup",
    ],
)
#
#rollup_bundle(
#    entry_point,
#    name = "my_build_rollup",
#    srcs = [
#        "bundle/gr-app.js",
#    ],
#)

polygerrit_bundle(
    name = "polygerrit_ui",
    outs = ["polygerrit_ui.zip"],
    app = "elements/gr-app.html",
    bundle = "bundle/gr-app",
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

filegroup(
    name = "some_modules",
    srcs = [
        "@test_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

sh_test(
    name = "wct_test",
    size = "enormous",
    srcs = ["wct_test.sh"],
    data = [
        "test/common-test-setup.html",
        "test/index.html",
        "redirects.json",
        ":pg_code",
        ":some_modules",
        "//tools/node_tools:code_utils.js",
        "//tools/node_tools:links_updater.js",
        #":test_components.zip",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

filegroup(
    name = "abc",
    srcs = [":pg_code"],
)

sh_test(
    name = "lint_test",
    size = "large",
    srcs = ["lint_test.sh"],
    data = [
        ".eslintrc.json",
        ":abc",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

nodejs_test(
    name = "polylint_test",
    size = "large",
    data = [
        "polymer.json",
        ":pg_code_without_test",
        "//tools/node_tools:cwd.js",
        "@tools_npm//polymer-cli",
    ],
    entry_point = "@tools_npm//:node_modules/polymer-cli/bin/polymer.js",
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
    templated_args = [
        "--node_options=--require",
        "--node_options=./tools/node_tools/cwd.js",
        "lint",
        "--verbose",
    ],
)

#sh_test(
#    name = "polylint_test",
#    size = "large",
#    srcs = ["polylint_test.sh"],
#    data = [
#        "polymer.json",
#        ":pg_code_without_test",
#    ],
#    # Should not run sandboxed.
#    tags = [
#        "local",
#        "manual",
#    ],
#)

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
