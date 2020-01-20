load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/node_tools/polygerrit_app_preprocessor:index.bzl", "prepare_for_bundling", "update_links")
load("//tools/node_tools/legacy:index.bzl", "polymer_bundler_tool")
load("@npm_bazel_rollup//:index.bzl", "rollup_bundle")
load(
    "//tools/bzl:js.bzl",
    "bundle_assets",
)

def polygerrit_bundle(name, srcs, outs, entry_point, redirects):
    app_name = entry_point.split(".html")[0].split("/").pop()  # eg: gr-app

    update_links(
        name = app_name + "-updated-links",
        srcs = srcs,
        redirects = redirects,
    )

    prepare_for_bundling(
        name = app_name + "-prebundling-srcs",
        srcs = [
            app_name + "-updated-links",
        ],
        additional_node_modules_to_preprocess = [
            "@ui_npm//polymer-bridges",
        ],
        entry_point = entry_point,
        node_modules = [
            "@ui_npm//:node_modules",
        ],
        root_path = "polygerrit-ui/app/" + app_name + "-updated-links/polygerrit-ui/app",
    )

    native.filegroup(
        name = app_name + "-prebundling-srcs-js",
        srcs = [app_name + "-prebundling-srcs"],
        output_group = "js",
    )

    native.filegroup(
        name = app_name + "-prebundling-srcs-html",
        srcs = [app_name + "-prebundling-srcs"],
        output_group = "html",
    )

    rollup_bundle(
        name = app_name + "-bundle-js",
        srcs = [app_name + "-prebundling-srcs-js"],
        config_file = ":rollup.config.js",
        entry_point = app_name + "-prebundling-srcs/entry.js",
        rollup_bin = "//tools/node_tools:rollup-bin",
        sourcemap = "hidden",
    )

    polymer_bundler_tool(
        name = app_name + "-bundle-html",
        srcs = [app_name + "-prebundling-srcs-html"],
        entry_point = app_name + "-prebundling-srcs/entry.html",
        script_src_value = app_name + ".js",
    )

    native.filegroup(
        name = name + "_app_sources",
        srcs = [
            app_name + "-bundle-js.js",
            app_name + "-bundle-html.html",
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
            "for f in $(locations " + name + "_app_sources); do ext=$${f##*.}; cp -p $$f $$TMP/polygerrit_ui/elements/" + app_name + ".$$ext; done",
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
