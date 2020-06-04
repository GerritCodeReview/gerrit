load("@io_bazel_rules_go//go:def.bzl", "go_binary")
load("//tools/bzl:genrule2.bzl", "genrule2")

package(default_visibility = ["//visibility:public"])

genrule2(
    name = "fonts",
    srcs = [
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

go_binary(
    name = "devserver",
    srcs = ["server.go"],
    data = [
        ":fonts.zip",
        "@ui_dev_npm//:node_modules",
        "@ui_npm//:node_modules",
    ],
    deps = [
        "@org_golang_x_tools//godoc/vfs/httpfs:go_default_library",
        "@org_golang_x_tools//godoc/vfs/zipfs:go_default_library",
    ],
)

# Define a karma+plugins binary to run karma-mocha tests.
# Can be reused multiple time, if there are multiple karma test rules
sh_binary(
    name = "karma_bin",
    srcs = ["@ui_dev_npm//:node_modules/karma/bin/karma"],
    data = [
        "@ui_dev_npm//@open-wc/karma-esm",
        "@ui_dev_npm//chai",
        "@ui_dev_npm//karma-chrome-launcher",
        "@ui_dev_npm//karma-mocha",
        "@ui_dev_npm//karma-mocha-reporter",
        "@ui_dev_npm//karma/bin:karma",
        "@ui_dev_npm//mocha",
    ],
)

# Run all tests in one.
# TODO(dmfilippov): allow parallel tests for karma - either on the bazel level
# or on the karma level. For now single sh_test is enough.
sh_test(
    name = "karma_test",
    size = "enormous",
    srcs = ["karma_test.sh"],
    args = [
        "$(location :karma_bin)",
        "$(location karma.conf.js)",
    ],
    data = [
        "karma.conf.js",
        ":karma_bin",
        "//polygerrit-ui/app:test-srcs-fg",
    ],
    # Should not run sandboxed.
    tags = [
        "karma",
        "local",
        "manual",
    ],
)
