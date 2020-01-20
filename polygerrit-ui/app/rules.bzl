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

    # Update links in all .html files according to rules in redirects.json file
    # All other files remains unchanged.
    # After update, all references to bower_components are replaced by a correct references
    # to a node_modules
    # The output of this rule is a directory, which mirrors directories layout of srcs files
    update_links(
        name = app_name + "-updated-links",
        srcs = srcs,
        redirects = redirects,
    )

    # Polymer 3 uses ES modules; gerrit still use HTML imports and polymer-bridges.
    # In such conditions, polymer-bundler/crisper and polymer-cli tools crash without an error
    # or complains about non-existing syntax error in .js code.
    # But even if they works with some config, the output result is not correct.
    # At the same time, polymer-bundler/crisper work well if input files are HTML and js
    # without javascript modules.
    #
    # Polygerrit's code follows simple rules, so it is quite easy to preprocess code
    # in a way, that it can be consumed by polymer-bundler/crisper tool.
    # Rules do the following:
    # 1) prepare_for_bundling - update srcs by moving all scripts out of HTML files.
    #    For each HTML file it creates file.html.js file in the same directory and put all scripts there
    #    in the same order, as script tags appear in HTML file.
    #    - Inline javascript is copied as is;
    #    - <script src = "path/to/file.js" > adds to .js file as
    #      import 'path/to/file.js'
    #      statement. Such import statement run all side-effects in file.js (i.e. it run all    #
    #    global code).
    #    - <link rel="import" href = "path/to/file.html"> adds to .js file as
    #      import 'path/to/file.html.js' - i.e. instead of html, the .js script imports
    #      another generated js file.
    #    Because output JS keeps the order of imports, all global variables are
    #    initialized in a correct order (this is important for gerrit; it is impossible to use
    #    AMD modules here).
    #    Then, all scripts are removed from HTML file.
    #    Output of this rule - directory with updated HTML and JS files; all other files
    #    are copied to the output directory without changes.
    # 2) rollup_bundle - combines all .js files from the previous step into one bundle.
    # 3) polymer_bundler_tool -
    #    a) run polymer-bundle tool on HTML files (i.e. on output from the
    #       first step). Because these files don't contain scripts anymore, it just combine
    #       all HTML/CSS files in one file (by following HTML imports).
    #    b) run crisper to add script tag at the end of generated HTML
    # Output of the rule is 2 file: HTML bundle and JS bundle and HTML file
    # loads JS file with <script src="..."> tag.

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
        deps = [
            "@tools_npm//rollup-plugin-node-resolve",
        ],
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
