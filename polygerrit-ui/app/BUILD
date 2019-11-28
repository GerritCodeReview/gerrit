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
    name = "release_src",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "test/**",
            "**/*_test.html",
            "node_modules/**",
        ],
    ),
)

sh_binary(
    name = "zip_files",
    srcs = ["//tools/bzl:zip_files.sh"],
)

genrule(
    name = "collect_release_files",
    srcs = [
        ":release_src",
    ],
    outs = ["release_src.zip"],
    cmd = " && ".join([
        "$(location :zip_files) $@ polygerrit-ui/app $(@D)/tmp $(locations :release_src) ",
    ]),
    tools = [
        ":zip_files",
    ],
)

genrule(
    name = "preprocess_release_files",
    srcs = [
        ":release_src.zip",
        "redirects.json",
    ],
    outs = [
        "preprocessed.zip",
    ],
    cmd = " && ".join([
        "mkdir -p $(@D)/tmp/src",
        "unzip -q $(location :release_src.zip) -d $(@D)/tmp/src",
        "$(location //tools/node_tools:links_updater) $(@D)/tmp/src $(@D)/tmp/src . $$(pwd)/$(location redirects.json)",
        "$(location //tools/node_tools:app_preprocessor) $(@D)/tmp/src $(@D)/tmp/preprocessed elements/gr-app.html",
        "ls -l $(@D)/tmp/preprocessed/",
        "ls -l $(@D)/tmp/preprocessed/behaviors/",
        "ls -l $(@D)/tmp/preprocessed/behaviors/async-foreach-behavior",
        "echo $(@D)/tmp/preprocessed/**/*",
        "$(location :zip_files) $@ $(@D)/tmp/preprocessed $(@D)/tmp/zip $(@D)/tmp/preprocessed/**/*",
        "rm -rf $(@D)/tmp",
    ]),
    tools = [
        ":zip_files",
        "//tools/node_tools:app_preprocessor",
        "//tools/node_tools:links_updater",
    ],
)

genrule(
    name = "make_bundle",
    srcs = [
        ":preprocessed.zip",
        "rollup.config.js",
        "@ui_npm//:node_modules",
    ],
    outs = [
        "bundle/gr-app.html",
        "bundle/gr-app.js",
    ],
    cmd = " && ".join([
        "mkdir -p $(@D)/tmp/src",
        "mkdir -p $(@D)/tmp/bundle",
        "unzip -q $(location :preprocessed.zip) -d $(@D)/tmp/src",
        "$(location //tools/node_tools:polymer-bundler) --root $(@D)/tmp/src --out-file $(@D)/tmp/bundle/gr-app.html elements/gr-app.html",
        "$(location //tools/node_tools:crisper) -s $(@D)/tmp/bundle/gr-app.html -h $(@D)/bundle/gr-app.html -j $(@D)/bundle/gr-app.js --always-write-script",
        "ln -s $$(pwd)/external/ui_npm/node_modules $(@D)/tmp/src/node_modules",
        "cp $(location rollup.config.js) $(@D)/tmp/src",
        "p=$$PWD",
        "cd $(@D)/tmp/src",
        "ls -l node_modules/",
        "$$p/$(location //tools/node_tools:rollup) -c rollup.config.js --input elements/gr-app.html_generated.js --file gr-app-bundle.js",
        "cd $$p",
        "cp $(@D)/tmp/src/gr-app-bundle.js $(@D)/bundle/gr-app.js",
        "rm -rf $(@D)/tmp",
    ]),
    tools = [
        "//tools/node_tools:crisper",
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
        "@ui_dev_npm//:node_modules",
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
