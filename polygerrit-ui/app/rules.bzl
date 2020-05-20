load("//tools/bzl:genrule2.bzl", "genrule2")
load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")

def _get_ts_compiled_path(outdir, file_name):
    """Returns the path to the file compiled by typescript.

    Args:
      outdir: the typescript output directory (relative to polygerrit-ui/app/)
      file_name: the original file name (relative to polygerrit-ui/app/)
    """
    if file_name.endswith(".js"):
        return outdir + "/" + file_name
    if file_name.endswith(".ts"):
        return outdir + "/" + file_name[:-2] + "js"
    fail("The file " + file_name + " has unsupported extension")

def _get_ts_output_files(outdir, srcs):
    """Returns the list of files produced by the typescript compiler

    Args:
      outdir: the typescript output directory (relative to polygerrit-ui/app/)
      srcs: list of input files (all paths relative to polygerrit-ui/app/)
    """
    result = []
    for f in srcs:
        if f.endswith(".d.ts"):
            continue
        result.append(_get_ts_compiled_path(outdir, f))
    return result

def polygerrit_bundle(name, srcs, outs, entry_point):
    """Build .zip bundle from source code

    Args:
        name: rule name
        srcs: source files
        outs: array with a single item - the output file name
        entry_point: application entry-point
    """

    ts_rule_name = name + "_ts_compiled"

    # Output directory for compiled files
    ts_outdir = "__ts__out__" + name

    # List of files produced by the typescript compiler
    generated_js = _get_ts_output_files(ts_outdir, srcs)

    # Run the compiler
    native.genrule(
        name = ts_rule_name,
        srcs = srcs + [
            ":tsconfig.json",
            "@ui_npm//:node_modules",
        ],
        outs = generated_js,
        cmd = " && ".join([
            "$(location //tools/node_tools:tsc-bin) --project $(location :tsconfig.json) --outdir $(RULEDIR)/" + ts_outdir + " --baseUrl ./external/ui_npm/node_modules",
        ]),
        tools = ["//tools/node_tools:tsc-bin"],
    )

    app_name = entry_point.split(".html")[0].split("/").pop()  # eg: gr-app

    native.filegroup(
        name = app_name + "-full-src",
        srcs = generated_js + [
            "@ui_npm//:node_modules",
        ],
    )

    rollup_bundle(
        name = app_name + "-bundle-js",
        srcs = [app_name + "-full-src"],
        config_file = ":rollup.config.js",
        entry_point = ts_outdir + "/elements/" + app_name + ".js",
        rollup_bin = "//tools/node_tools:rollup-bin",
        sourcemap = "hidden",
        deps = [
            "@tools_npm//rollup-plugin-node-resolve",
        ],
    )

    native.filegroup(
        name = name + "_app_sources",
        srcs = [
            app_name + "-bundle-js.js",
            entry_point,
        ],
    )

    native.filegroup(
        name = name + "_css_sources",
        srcs = native.glob(["styles/**/*.css"]),
    )

    native.filegroup(
        name = name + "_theme_sources",
        srcs = native.glob(
            ["styles/themes/*.html"],
            # app-theme.html already included via an import in gr-app.html.
            exclude = ["styles/themes/app-theme.html"],
        ),
    )

    native.filegroup(
        name = name + "_top_sources",
        srcs = [
            "favicon.ico",
        ],
    )

    # Preserve bower_components directory in the final directory layout to
    # avoid plugins break
    genrule2(
        name = name,
        srcs = [
            name + "_app_sources",
            name + "_css_sources",
            name + "_theme_sources",
            name + "_top_sources",
            "//lib/fonts:robotofonts",
            "//lib/js:highlightjs__files",
            "@ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js",
            "@ui_npm//@polymer/font-roboto-local",
            "@ui_npm//:node_modules/@polymer/font-roboto-local/package.json",
        ],
        outs = outs,
        cmd = " && ".join([
            "FONT_DIR=$$(dirname $(location @ui_npm//:node_modules/@polymer/font-roboto-local/package.json))/fonts",
            "mkdir -p $$TMP/polygerrit_ui/{styles/themes,fonts/{roboto,robotomono},bower_components/{highlightjs,webcomponentsjs},elements}",
            "for f in $(locations " + name + "_app_sources); do ext=$${f##*.}; cp -p $$f $$TMP/polygerrit_ui/elements/" + app_name + ".$$ext; done",
            "cp $(locations //lib/fonts:robotofonts) $$TMP/polygerrit_ui/fonts/",
            "for f in $(locations " + name + "_top_sources); do cp $$f $$TMP/polygerrit_ui/; done",
            "for f in $(locations " + name + "_css_sources); do cp $$f $$TMP/polygerrit_ui/styles; done",
            "for f in $(locations " + name + "_theme_sources); do cp $$f $$TMP/polygerrit_ui/styles/themes; done",
            "for f in $(locations //lib/js:highlightjs__files); do cp $$f $$TMP/polygerrit_ui/bower_components/highlightjs/ ; done",
            "cp $(location @ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js) $$TMP/polygerrit_ui/bower_components/webcomponentsjs/webcomponents-lite.js",
            "cp $$FONT_DIR/roboto/*.ttf $$TMP/polygerrit_ui/fonts/roboto/",
            "cp $$FONT_DIR/robotomono/*.ttf $$TMP/polygerrit_ui/fonts/robotomono/",
            "cd $$TMP",
            "find . -exec touch -t 198001010000 '{}' ';'",
            "zip -qr $$ROOT/$@ *",
        ]),
    )

def _wct_test(name, srcs, split_index, split_count):
    """Macro to define single WCT suite

    Defines a private macro for a portion of test files with split_index.
    The actual split happens in test/tests.js file

    Args:
        name: name of generated sh_test
        srcs: source files
        split_index: index WCT suite. Must be less than split_count
        split_count: total number of WCT suites
    """
    str_index = str(split_index)
    config_json = struct(splitIndex = split_index, splitCount = split_count).to_json()
    native.sh_test(
        name = name,
        size = "enormous",
        srcs = ["wct_test.sh"],
        args = [
            "$(location @ui_dev_npm//web-component-tester/bin:wct)",
            config_json,
        ],
        data = [
            "@ui_dev_npm//web-component-tester/bin:wct",
        ] + srcs,
        # Should not run sandboxed.
        tags = [
            "local",
            "manual",
            "wct",
        ],
    )

def wct_suite(name, srcs, split_count):
    """Define test suites for WCT tests.

    All tests files are split to split_count WCT suites

    Args:
        name: rule name. The macro create a test suite rule with the name name+"_test"
        srcs: source files
        split_count: number of sh_test (i.e. WCT suites)
    """
    tests = []
    for i in range(split_count):
        test_name = "wct_test_" + str(i)
        _wct_test(test_name, srcs, i, split_count)
        tests.append(test_name)

    native.test_suite(
        name = name + "_test",
        tests = tests,
        # Setup tags for suite as well.
        # This excludes tests from the wildcard expansion (//...)
        tags = [
            "local",
            "manual",
        ],
    )
