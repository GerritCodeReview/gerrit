load("@rules_java//java:defs.bzl", "java_library")
load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")
load("//tools/js:eslint.bzl", "eslint")
load("//tools/bzl:plugin.bzl", "gerrit_plugin")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "polygerrit_plugin")

gerrit_plugin(
    name = "ac-gerrit",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: ac-gerrit",
        "Gerrit-Module: com.audiocodes.gwapp.plugins.acGerrit.Module",
        "Gerrit-HttpModule: com.audiocodes.gwapp.plugins.acGerrit.HttpModule",
    ],
    resource_jars = [":ac-polygerrit-static"],
    resources = glob(["src/main/resources/**/*"]),
)

genrule2(
    name = "ac-polygerrit-static",
    srcs = [":ac-polygerrit"],
    outs = ["ac-polygerrit-static.jar"],
    cmd = " && ".join([
        "mkdir $$TMP/static",
        "cp -r $(locations :ac-polygerrit) $$TMP/static",
        "cd $$TMP",
        "zip -Drq $$ROOT/$@ -g .",
    ]),
)

polygerrit_plugin(
    name = "ac-polygerrit",
    app = "ac-gerrit-bundle.js",
    plugin_name = "ac-polygerrit",
)

rollup_bundle(
    name = "ac-gerrit-bundle",
    srcs = glob(["ac-polygerrit/*.js"]),
    entry_point = "ac-polygerrit/plugin.js",
    format = "iife",
    rollup_bin = "//tools/node_tools:rollup-bin",
    sourcemap = "hidden",
    deps = [
        "@tools_npm//rollup-plugin-node-resolve",
    ],
)

# Define the eslinter for the plugin
# The eslint macro creates 2 rules: lint_test and lint_bin
eslint(
    name = "lint",
    srcs = glob([
        "ac-polygerrit/*.js",
    ]),
    config = ".eslintrc.json",
    data = [],
    extensions = [
        ".js",
    ],
    ignore = ".eslintignore",
    plugins = [
        "@npm//eslint-config-google",
        "@npm//eslint-plugin-html",
        "@npm//eslint-plugin-import",
        "@npm//eslint-plugin-jsdoc",
    ],
)
