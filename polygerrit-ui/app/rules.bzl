load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load(
    "//tools/bzl:js.bzl",
    "bundle_assets",
)

def polygerrit_bundle(name, srcs, outs, app):
    appName = app.split(".html")[0].split("/").pop()  # eg: gr-app

    closure_js_binary(
        name = name + "_closure_bin",
        # Known issue: Closure compilation not compatible with Polymer behaviors.
        # See: https://github.com/google/closure-compiler/issues/2042
        compilation_level = "WHITESPACE_ONLY",
        defs = [
            "--polymer_version=1",
            "--jscomp_off=duplicate",
            "--force_inject_library=es6_runtime",
        ],
        language = "ECMASCRIPT5",
        deps = [name + "_closure_lib"],
        dependency_mode = "PRUNE_LEGACY",
    )

    # TODO(davido): Remove JSC_REFERENCE_BEFORE_DECLARE when this is fixed upstream:
    # https://github.com/Polymer/polymer-resin/issues/7
    closure_js_library(
        name = name + "_closure_lib",
        srcs = [appName + ".js"],
        convention = "GOOGLE",
        # TODO(davido): Clean up these issues: http://paste.openstack.org/show/608548
        # and remove this supression
        suppress = [
            "JSC_JSDOC_MISSING_TYPE_WARNING",
            "JSC_REFERENCE_BEFORE_DECLARE",
            "JSC_UNNECESSARY_ESCAPE",
            "JSC_UNUSED_LOCAL_ASSIGNMENT",
        ],
        deps = [
            "//lib/polymer_externs:polymer_closure",
            "@io_bazel_rules_closure//closure/library",
        ],
    )

    bundle_assets(
        name = appName,
        srcs = srcs,
        app = app,
        deps = ["//polygerrit-ui:polygerrit_components.bower_components"],
    )

    native.filegroup(
        name = name + "_app_sources",
        srcs = [
            name + "_closure_bin.js",
            appName + ".html",
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

    genrule2(
        name = name,
        srcs = [
            name + "_app_sources",
            name + "_css_sources",
            name + "_theme_sources",
            name + "_top_sources",
            "//lib/fonts:robotofonts",
            "//lib/js:highlightjs_files",
            # we extract from the zip, but depend on the component for license checking.
            "@webcomponentsjs//:zipfile",
            "//lib/js:webcomponentsjs",
            "@font-roboto-local//:zipfile",
            "//lib/js:font-roboto-local",
        ],
        outs = outs,
        cmd = " && ".join([
            "mkdir -p $$TMP/polygerrit_ui/{styles/themes,fonts,bower_components/{highlightjs,webcomponentsjs},elements}",
            "for f in $(locations " + name + "_app_sources); do ext=$${f##*.}; cp -p $$f $$TMP/polygerrit_ui/elements/" + appName + ".$$ext; done",
            "cp $(locations //lib/fonts:robotofonts) $$TMP/polygerrit_ui/fonts/",
            "for f in $(locations " + name + "_top_sources); do cp $$f $$TMP/polygerrit_ui/; done",
            "for f in $(locations " + name + "_css_sources); do cp $$f $$TMP/polygerrit_ui/styles; done",
            "for f in $(locations " + name + "_theme_sources); do cp $$f $$TMP/polygerrit_ui/styles/themes; done",
            "for f in $(locations //lib/js:highlightjs_files); do cp $$f $$TMP/polygerrit_ui/bower_components/highlightjs/ ; done",
            "unzip -qd $$TMP/polygerrit_ui/bower_components $(location @webcomponentsjs//:zipfile) webcomponentsjs/webcomponents-lite.js",
            "unzip -qd $$TMP/polygerrit_ui/bower_components $(location @font-roboto-local//:zipfile) font-roboto-local/fonts/\\*/\\*.ttf",
            "cd $$TMP",
            "find . -exec touch -t 198001010000 '{}' ';'",
            "zip -qr $$ROOT/$@ *",
        ]),
    )
<<<<<<< HEAD   (a008f3 Update git submodules)
=======

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
        ],
    )

def wct_suite(name, srcs, split_count):
    """Define test suites for WCT tests.

    All tests files are splited to split_count WCT suites

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
>>>>>>> CHANGE (ae42cd Fix tests for master branch)
