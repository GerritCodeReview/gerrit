load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "web_test_runner")
load("@ui_dev_npm//:defs.bzl", "npm_link_all_packages")

npm_link_all_packages(name = "node_modules")

package(default_visibility = ["//visibility:public"])

genrule2(
    name = "fonts",
    srcs = [
        "//lib/fonts:material-icons",
        "//lib/fonts:robotofonts",
    ],
    outs = ["fonts.zip"],
    cmd = " && ".join([
        "mkdir -p $$TMP/fonts",
        "cp $(SRCS) $$TMP/fonts/",
        "cd $$TMP",
        "find fonts/ -exec touch -t 198001010000 '{}' ';'",
        "zip -qr $$ROOT/$@ fonts",
    ]),
    output_to_bindir = 1,
)

# This is a dependency for web_test_runner rule in js.bzl that is only used by
# plugins.
sh_binary(
    name = "web_test_runner_bin",
    srcs = ["//polygerrit-ui:node_modules/@web/test-runner/dist/bin.js"],
    data = [
        "//polygerrit-ui:node_modules/@web/dev-server-esbuild",
        "//polygerrit-ui:node_modules/@web/test-runner",
        "//polygerrit-ui:node_modules/@web/test-runner-playwright",
        "//polygerrit-ui:node_modules/@web/test-runner-visual-regression",
    ],
)

web_test_runner(
    name = "web_test_runner",
    srcs = ["web_test_runner.sh"],
    data = [
        "//polygerrit-ui:node_modules",
        "//polygerrit-ui/app:node_modules",
        "//polygerrit-ui/app:web-test-runner_app-sources",
    ],
)

# This is used by plugins.
exports_files(["web-test-runner.config.mjs"])
