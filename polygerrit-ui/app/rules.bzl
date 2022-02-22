load("//tools/bzl:genrule2.bzl", "genrule2")
load("@npm//@bazel/rollup:index.bzl", "rollup_bundle")

def polygerrit_bundle(name, srcs, outs, entry_point, app_name):
    """Build .zip bundle from source code

    Args:
        name: rule name
        srcs: source files
        outs: array with a single item - the output file name
        entry_point: application js entry-point
        app_name: defines the application name. Bundled js code is added to .zip
          archive with this name.
    """

    native.filegroup(
        name = app_name + "-full-src",
        srcs = srcs + [
            "@ui_npm//:node_modules",
        ],
    )

    rollup_bundle(
        name = app_name + "-bundle-js",
        srcs = [app_name + "-full-src"],
        config_file = ":rollup.config.js",
        entry_point = entry_point,
        rollup_bin = "//tools/node_tools:rollup-bin",
        silent = True,
        sourcemap = "hidden",
        deps = [
            "@tools_npm//rollup-plugin-node-resolve",
        ],
    )

    rollup_bundle(
        name = "syntax-worker",
        srcs = [app_name + "-full-src"],
        config_file = ":rollup.config.js",
        entry_point = "_pg_ts_out/workers/syntax-worker.js",
        rollup_bin = "//tools/node_tools:rollup-bin",
        silent = True,
        sourcemap = "hidden",
        deps = [
            "@tools_npm//rollup-plugin-node-resolve",
        ],
    )

    native.filegroup(
        name = name + "_app_sources",
        srcs = [
            app_name + "-bundle-js.js",
        ],
    )

    native.filegroup(
        name = name + "_css_sources",
        srcs = native.glob(["styles/**/*.css"]),
    )

    native.filegroup(
        name = name + "_worker_sources",
        srcs = [
            "syntax-worker.js",
        ],
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
            name + "_top_sources",
            name + "_worker_sources",
            "//lib/fonts:robotofonts",
            "//lib/js:highlightjs__files",
            "@ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js",
            "@ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js.map",
            "@ui_npm//:node_modules/resemblejs/resemble.js",
            "@ui_npm//@polymer/font-roboto-local",
            "@ui_npm//:node_modules/@polymer/font-roboto-local/package.json",
        ],
        outs = outs,
        cmd = " && ".join([
            "FONT_DIR=$$(dirname $(location @ui_npm//:node_modules/@polymer/font-roboto-local/package.json))/fonts",
            "mkdir -p $$TMP/polygerrit_ui/{workers,styles/themes,fonts/{roboto,robotomono},bower_components/{highlightjs,webcomponentsjs,resemblejs},elements}",
            "for f in $(locations " + name + "_app_sources); do ext=$${f##*.}; cp -p $$f $$TMP/polygerrit_ui/elements/" + app_name + ".$$ext; done",
            "cp $(locations //lib/fonts:robotofonts) $$TMP/polygerrit_ui/fonts/",
            "for f in $(locations " + name + "_top_sources); do cp $$f $$TMP/polygerrit_ui/; done",
            "for f in $(locations " + name + "_css_sources); do cp $$f $$TMP/polygerrit_ui/styles; done",
            "for f in $(locations " + name + "_worker_sources); do cp $$f $$TMP/polygerrit_ui/workers; done",
            "for f in $(locations //lib/js:highlightjs__files); do cp $$f $$TMP/polygerrit_ui/bower_components/highlightjs/ ; done",
            "cp $(location @ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js) $$TMP/polygerrit_ui/bower_components/webcomponentsjs/webcomponents-lite.js",
            "cp $(location @ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js.map) $$TMP/polygerrit_ui/bower_components/webcomponentsjs/webcomponents-lite.js.map",
            "cp $(location @ui_npm//:node_modules/resemblejs/resemble.js) $$TMP/polygerrit_ui/bower_components/resemblejs/resemble.js",
            "cp $$FONT_DIR/roboto/*.ttf $$TMP/polygerrit_ui/fonts/roboto/",
            "cp $$FONT_DIR/robotomono/*.ttf $$TMP/polygerrit_ui/fonts/robotomono/",
            "cd $$TMP",
            "find . -exec touch -t 198001010000 '{}' ';'",
            "zip -qr $$ROOT/$@ *",
        ]),
    )
