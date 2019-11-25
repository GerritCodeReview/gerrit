load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load(
    "//tools/bzl:js.bzl",
    "bundle_assets",
)

def polygerrit_bundle(name, bundle, outs, app):
    appName = app.split(".html")[0].split("/").pop()  # eg: gr-app

    native.filegroup(
        name = name + "_app_sources",
        srcs = [
            bundle + ".js",
            bundle + ".html",
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
            "@ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js",
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
            "mkdir webcomponentsjs && cp -p $(location @ui_npm//:node_modules/@webcomponents/webcomponentsjs/webcomponents-lite.js) webcomponentsjs/webcomponents-lite.js",
            "cd $$TMP",
            "find . -exec touch -t 198001010000 '{}' ';'",
            "zip -qr $$ROOT/$@ *",
        ]),
    )

