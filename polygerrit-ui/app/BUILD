load("//tools/bzl:genrule2.bzl", "genrule2")
load(":rules.bzl", "polygerrit_bundle")

#load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "src_for_build",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
            "redirects.json",
            "rollup.config.js"
        ],
        exclude = [
            "test/**",
            "**/*_test.html",
            "html_to_js.js",
        ],
    ),
)

genrule(
    name = "polygerrit_src_bundle",
    srcs = [":src_for_build", "@ui_npm//:node_modules"],
    outs = [
        "bundle/gr-app.html",
        "bundle/gr-app.js",
    ],
    cmd = " && ".join([
        "$(location //tools/node_tools:app_preprocessor) polygerrit-ui $(@D)/out app/elements/gr-app.html polygerrit-ui/app/redirects.json",
        "mkdir $(@D)/out/bundle",
        "$(location //tools/node_tools:polymer-bundler) --out-file $(@D)/out/bundle/gr-app.html $(@D)/out/app/elements/gr-app.html",
        "$(location //tools/node_tools:crisper) -s $(@D)/out/bundle/gr-app.html -h $(@D)/bundle/gr-app.html -j $(@D)/bundle/gr-app.js --script-in-head false --always-write-script",
        "ls -l polygerrit-ui/app",
        "$(location //tools/node_tools:rollup) -c polygerrit-ui/app/rollup.config.js --input $(@D)/out/app/elements/gr-app.html_generated.js --file $(@D)/bundle/gr-app.js",
        "rm -rf $(@D)/out",
    ]),
    tools = [
        "//tools/node_tools:app_preprocessor",
        "//tools/node_tools:crisper",
        "//tools/node_tools:polymer-bundler",
        "//tools/node_tools:rollup"
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

polygerrit_bundle(name="polygerrit_ui",
                      bundle="bundle/gr-app", outs=["polygerrit_ui.zip"], app="elements/gr-app.html")

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
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
            "embed/test.html",
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


polygerrit_bundle(name="polygerrit_embed_ui",
                      bundle="bundle/gr-app", outs=["polygerrit_embed_ui.zip"], app="elements/gr-app.html")

# Embed bundle
#polygerrit_bundle(
#    name = "polygerrit_embed_ui",
#    srcs = glob(
#        [
#            "**/*.html",
#            "**/*.js",
#        ],
#        exclude = [
#            "bower_components/**",
#            "test/**",
#            "**/*_test.html",
#        ],
#    ),
#    outs = ["polygerrit_embed_ui.zip"],
#    app = "embed/embed.html",
#)

filegroup(
    name = "embed_test_files",
    srcs = glob(
        [
            "embed/**/*_test.html",
        ],
    ),
)

filegroup(
    name = "template_test_srcs",
    srcs = [
        "template_test_srcs/convert_for_template_tests.py",
        "template_test_srcs/template_test.js",
    ],
)

sh_test(
    name = "embed_test",
    size = "small",
    srcs = ["embed_test.sh"],
    data = [
        "embed/test.html",
        "test/common-test-setup.html",
        ":embed_test_files",
        ":pg_code.zip",
        ":test_components.zip",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)
