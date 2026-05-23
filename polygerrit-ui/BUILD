load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "web_test_runner")

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
    srcs = ["@ui_dev_npm//:node_modules/@web/test-runner/dist/bin.js"],
    data = [
        "@ui_dev_npm//@web/dev-server-esbuild",
        "@ui_dev_npm//@web/test-runner",
        "@ui_dev_npm//@web/test-runner-playwright",
        "@ui_dev_npm//@web/test-runner-visual-regression",
    ],
)

web_test_runner(
    name = "web_test_runner",
    srcs = ["web_test_runner.sh"],
    data = [
        "//polygerrit-ui/app:web-test-runner_app-sources",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
)

# Baseline PNGs used by *_screenshot_test.ts files. The failed/ directory is
# gitignored and intentionally excluded.
filegroup(
    name = "screenshot_baselines",
    srcs = glob(["screenshots/**/baseline/**"]),
)

web_test_runner(
    name = "web_test_runner_screenshots",
    srcs = ["web_test_runner.sh"],
    args = ["--run-screenshots"],
    data = [
        "//polygerrit-ui/app:web-test-runner_app-sources",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
        ":screenshot_baselines",
        # fonts.css references /polygerrit-ui/app/fonts/, which the runtime
        # middleware in web-test-runner.config.mjs rewrites to /lib/fonts/.
        # Pull the actual font files into the runfiles so that rewrite
        # resolves to a real path; otherwise glyphs fall back to system
        # fonts and every screenshot differs in height by a few pixels.
        "//lib/fonts:robotofonts",
        "//lib/fonts:material-icons",
    ],
)

# Convenience target that runs both the regular unit tests and the screenshot
# regression tests. Tagged manual so it is not picked up by `bazel test //...`;
# CI invokes it explicitly.
test_suite(
    name = "web_test_runner_all",
    tags = ["manual"],
    tests = [
        ":web_test_runner",
        ":web_test_runner_screenshots",
    ],
)

# This is used by plugins.
exports_files(["web-test-runner.config.mjs"])
