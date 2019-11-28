load("//tools/bzl:genrule2.bzl", "genrule2")
load(":rules.bzl", "polygerrit_bundle")
load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_test")

package(default_visibility = ["//visibility:public"])

PG_ALL_CODE_FILES = [
    "**/*.html",
    "**/*.js",
]

PG_RELEASE_CODE_FILES = [
    "behaviors/**/*.html",
    "behaviors/**/*.js",
    "elements/**/*.html",
    "elements/**/*.js",
    "scripts/**/*.html",
    "scripts/**/*.js",
    "styles/**/*.html",
    "styles/**/*.js",
    "types/**/*.html",
    "types/**/*.js",
]

PG_TEST_CODE_FILES = [
    "**/*_test.html",
]

SERVICE_FILES = [
    "node_modules/**",
    "node_modules_licenses/**",
]

filegroup(
    name = "pg_release_code_files",
    srcs = glob(
        PG_RELEASE_CODE_FILES,
        exclude = PG_TEST_CODE_FILES + SERVICE_FILES,
    ),
)

filegroup(
    name = "pg_test_code_files",
    srcs = glob(
        PG_ALL_CODE_FILES,
        exclude = PG_RELEASE_CODE_FILES + SERVICE_FILES,
    ),
)

sh_binary(
    name = "zip_files",
    srcs = ["//tools/bzl:zip_files.sh"],
)

# Create a .zip archive with all code files for polygerrit release.
# At the root level the archive contains all nested entries from app folder
# I.e. the archive has the following tree:
# release_src.zip
# |--behaviors
# |     |--...sub-entries of behaviors
# |--elements
# |     |--..sub-entries of elements
# | ...... other sub-entries of polygerrit-ui/app folder
genrule(
    name = "pkg_release_code_files",
    srcs = [
        ":pg_release_code_files",
    ],
    outs = ["release_src.zip"],
    cmd = " && ".join([
        "$(location :zip_files) $@ polygerrit-ui/app $(@D)/tmp $(locations :pg_release_code_files) ",
    ]),
    tools = [
        ":zip_files",
    ],
)

# Prepare source code for bundling.
# This is a temporary step, it will be removed after migrating to javascript modules.
# This rule process all .html and .js files from the input file (release_src.zip)
# and produce a new .zip file with updated/newly created files.
# The following steps are executed:
# - unpack release_src.zip to a temporary directory "src"
# - copy polymer_legacy_support package to the same "src/node_modules" directory. This step is nessecary, because
#   it also contains HTML imports, so it should be processed before build. All other packages must not be touched.
# - update links (html import and scripts) in all .html files. For details - see tools/node_tools/links_updater.js
#   This is an inplace update in "src" directory.
#   Information from redirects.json is used to perform updates.
# - Run app_preprocess to generate and/or extract .js file(or multiple files) from each .html file. This phase is needed, because
#   rollup tool can work only with .js files.
#   The result of this action is placed to a "preprocessed" directory. Additional, all original files (.js and .html)
#   are copied to the same "preprocessed" directory. For details see tools/node_tools/app_preprocessor.js
# - Archive "preprocessed" directory to pg_preprocessed.zip file.
#   This archive has the same directory layout as release_src.zip. Additionally, it contains all .js and .html files
#   from node_modules/polymer_legacy_support folder
# NOTE 1: all non .html and non .js files are ignored and is not present in the final archive.
# NOTE 2: The output of this rule is not a working code! .html files don't have any script tag inside.
#    All scripts are extracted to the separate .js modules.(for details - see tools/node_tools/app_preprocessor.js)
genrule(
    name = "preprocess_release_files",
    srcs = [
        ":release_src.zip",
        # Depending on polymer_legacy_support package only to avoid unnecessary dependencies.
        # Unnecessary dependencies can lead to a false alarm from license checker (aka license_map rule)
        "@ui_npm//polymer_legacy_support",
    ],
    outs = [
        "pg_preprocessed.zip",
    ],
    cmd = " && ".join([
        "mkdir -p $(@D)/tmp/src",
        "unzip -q $(location :release_src.zip) -d $(@D)/tmp/src",
        "mkdir -p $(@D)/tmp/src/node_modules",
        "cp -r -L $$(pwd)/external/ui_npm/node_modules/polymer_legacy_support $(@D)/tmp/src/node_modules",
        "chmod -R +w $(@D)/tmp/src/node_modules",
        "$(location //tools/node_tools:links_updater) $(@D)/tmp/src $(@D)/tmp/src . $$(pwd)/$(location redirects.json)",
        "$(location //tools/node_tools:app_preprocessor) $(@D)/tmp/src $(@D)/tmp/preprocessed elements/gr-app.html",
        "$(location :zip_files) $@ $(@D)/tmp/preprocessed $(@D)/tmp/zip $(@D)/tmp/preprocessed/**/*",
        "rm -rf $(@D)/tmp",
    ]),
    tools = [
        "redirects.json",
        ":zip_files",
        "//tools/node_tools:app_preprocessor",
        "//tools/node_tools:links_updater",
    ],
)

genrule(
    name = "html_bundle",
    srcs = [
        ":pg_preprocessed.zip",
    ],
    outs = [
        "bundle/gr-app.html",
    ],
    cmd = " && ".join([
        "mkdir -p $(@D)/tmp/src",
        "mkdir -p $(@D)/tmp/bundle",
        "unzip -q $(location :pg_preprocessed.zip) -d $(@D)/tmp/src",
        "$(location //tools/node_tools:polymer-bundler) --root $(@D)/tmp/src --out-file $(@D)/tmp/bundle/gr-app.html elements/gr-app.html",
        "$(location //tools/node_tools:crisper) -s $(@D)/tmp/bundle/gr-app.html -h $(@D)/gr-app.html -j $(@D)/gr-app.js --always-write-script --script-in-head=false",
        "rm -rf $(@D)/tmp",
    ]),
    tools = [
        "//tools/node_tools:crisper",
        "//tools/node_tools:polymer-bundler",
    ],
)

# Produce bundle for source code. The output bundle has 2 files - bundle/gr-app.html and bundle/gr-app.js
# This rule cosnists of the following steps:
# - extract files from pg_preprocessed.zip archive
# - Run polymer-bundler. Because .html files in preprocessed.zip don't contain <script...> tags,
#     polymer-bundler can bundle all html together following only html imports.
#     (polymer-bundler and crisper can't process correctly files with js-modules).
#   The result of this step is an .html with all polymer templates, etc...
# - Run crisper on the file from the previous step. In this rule crisper almost do nothing, it just add
#     one script tag in the appropriate place in the html file. The output of the crisper is
#     the bundle/gr-app.html
# - copy missing files from node_modules (preprocessed.zip already contains some files)
# - compile all .js files together using rollup.js. The output is the bundle/gr-app.js
# NOTE: the output of this rule is a fully functional application without additional resources
# (styles, fonts, etc...)
genrule(
    name = "js_bundle",
    srcs = [
        ":pg_preprocessed.zip",
        "@ui_npm//:node_modules",
    ],
    outs = [
        "bundle/gr-app.js",
    ],
    cmd = " && ".join([
        "mkdir -p $(@D)/tmp/src",
        "mkdir -p $(@D)/tmp/bundle",
        "unzip -q $(location :pg_preprocessed.zip) -d $(@D)/tmp/src",
        "cp -n -r $$(pwd)/external/ui_npm/node_modules/* $(@D)/tmp/src/node_modules",
        "cp $(location rollup.config.js) $(@D)/tmp/src",
        "p=$$PWD",
        "cd $(@D)/tmp/src",
        "$$p/$(location //tools/node_tools:rollup) -c rollup.config.js --input elements/gr-app.html_generated.js --file gr-app-bundle.js",
        "cd $$p",
        "cp $(@D)/tmp/src/gr-app-bundle.js $(@D)/gr-app.js",
        "rm -rf $(@D)/tmp",
    ]),
    tools = [
        "rollup.config.js",
        "//tools/node_tools:rollup",
    ],
)

filegroup(
    name = "testgroup",
    srcs = ["@ui_npm//:node_modules/polymer-resin/classify.js"],
)

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = [
        "bundle/gr-app.html",
        "bundle/gr-app.js",
    ],
    outs = ["polygerrit_ui.zip"],
    app = "elements/gr-app.html",
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
            "node_modules/**",
            "node_modules_licenses/**",
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

# polymer linter doesn't work with Polymer 3 - see https://github.com/Polymer/tools/issues/3475
# This rule doesn't do anything, keep this rule to avoid problems with CI.
# Rule should be either removed (need changes in CI) or implemented correctly.
sh_test(
    name = "polylint_test",
    size = "large",
    srcs = ["polylint_test.sh"],
    data = [],
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
