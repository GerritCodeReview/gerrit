"""Build rules for polygerrit."""

load("@aspect_rules_rollup//rollup:defs.bzl", "rollup")
load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")

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
            "//polygerrit-ui/app:node_modules",
        ],
    )

    rollup(
        name = app_name + "-bundle-js",
        srcs = [app_name + "-full-src"],
        node_modules = "//tools/node_tools:node_modules",
        args = [
            "--bundleConfigAsCjs=true",
        ],
        config_file = ":rollup.config.js",
        entry_point = entry_point,
        silent = True,
        sourcemap = "hidden",
        deps = [
            "//tools/node_tools:node_modules/@rollup/plugin-replace",
            "//tools/node_tools:node_modules/@rollup/plugin-node-resolve",
            "//tools/node_tools:node_modules/@rollup/plugin-terser",
        ],
    )

    rollup(
        name = "syntax-worker",
        srcs = [app_name + "-full-src"],
        node_modules = "//tools/node_tools:node_modules",
        args = [
            "--bundleConfigAsCjs=true",
        ],
        config_file = ":rollup.config.js",
        entry_point = "_pg_ts_out/workers/syntax-worker.js",
        silent = True,
        sourcemap = "hidden",
        deps = [
            "//tools/node_tools:node_modules/@rollup/plugin-replace",
            "//tools/node_tools:node_modules/@rollup/plugin-node-resolve",
            "//tools/node_tools:node_modules/@rollup/plugin-terser",
        ],
    )

    rollup(
        name = "service-worker",
        srcs = [app_name + "-full-src"],
        node_modules = "//tools/node_tools:node_modules",
        args = [
            "--bundleConfigAsCjs=true",
        ],
        config_file = ":rollup.config.js",
        entry_point = "_pg_ts_out/workers/service-worker.js",
        silent = True,
        sourcemap = "hidden",
        deps = [
            "//tools/node_tools:node_modules/@rollup/plugin-replace",
            "//tools/node_tools:node_modules/@rollup/plugin-node-resolve",
            "//tools/node_tools:node_modules/@rollup/plugin-terser",
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
            "service-worker.js",
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
            "//lib/fonts:material-icons",
            "//lib/fonts:robotofonts",
            "//lib/js:highlightjs__files",
            "//lib/js:emojis__files",
            "//polygerrit-ui/app:node_modules/resemblejs/dir",
            "//polygerrit-ui/app:node_modules/@polymer/font-roboto-local/dir",
        ],
        outs = outs,
        cmd = " && ".join([
            "FONT_DIR=$(location //polygerrit-ui/app:node_modules/@polymer/font-roboto-local/dir)/fonts",
            "RESEMBLEJS_DIR=$(location //polygerrit-ui/app:node_modules/resemblejs/dir)",
            "mkdir -p $$TMP/polygerrit_ui/{workers,styles/themes,fonts/{roboto,robotomono},bower_components/{emojis,highlightjs,resemblejs},elements}",
            "for f in $(locations " + name + "_app_sources); do ext=$${f##*.}; cp -p $$f $$TMP/polygerrit_ui/elements/" + app_name + ".$$ext; done",
            "cp $(locations //lib/fonts:robotofonts) $$TMP/polygerrit_ui/fonts/",
            "cp $(locations //lib/fonts:material-icons) $$TMP/polygerrit_ui/fonts/",
            "for f in $(locations " + name + "_top_sources); do cp $$f $$TMP/polygerrit_ui/; done",
            "for f in $(locations " + name + "_css_sources); do cp $$f $$TMP/polygerrit_ui/styles; done",
            "for f in $(locations " + name + "_worker_sources); do cp $$f $$TMP/polygerrit_ui/workers; done",
            "for f in $(locations //lib/js:highlightjs__files); do cp $$f $$TMP/polygerrit_ui/bower_components/highlightjs/ ; done",
            "for f in $(locations //lib/js:emojis__files); do cp $$f $$TMP/polygerrit_ui/bower_components/emojis/ ; done",
            "cp $$RESEMBLEJS_DIR/resemble.js $$TMP/polygerrit_ui/bower_components/resemblejs/resemble.js",
            "cp $$FONT_DIR/roboto/*.ttf $$TMP/polygerrit_ui/fonts/roboto/",
            "cp $$FONT_DIR/robotomono/*.ttf $$TMP/polygerrit_ui/fonts/robotomono/",
            "cd $$TMP",
            "find . -exec touch -t 198001010000 '{}' ';'",
            "zip -qr $$ROOT/$@ *",
        ]),
    )
