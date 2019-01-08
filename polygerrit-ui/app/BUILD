load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library")
load("//tools/bzl:genrule2.bzl", "genrule2")
load(
    "//tools/bzl:js.bzl",
    "bower_component_bundle",
    "vulcanize",
)

package(default_visibility = ["//visibility:public"])

vulcanize(
    name = "gr-app",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
            "index.html",
            "test/**",
            "**/*_test.html",
        ],
    ),
    app = "elements/gr-app.html",
    deps = ["//polygerrit-ui:polygerrit_components.bower_components"],
)

closure_js_library(
    name = "closure_lib",
    srcs = ["gr-app.js"],
    convention = "GOOGLE",
    # TODO(davido): Clean up these issues: http://paste.openstack.org/show/608548
    # and remove this supression
    suppress = [
        "JSC_JSDOC_MISSING_TYPE_WARNING",
        "JSC_UNNECESSARY_ESCAPE",
        "JSC_UNUSED_LOCAL_ASSIGNMENT",
    ],
    deps = [
        "//lib/polymer_externs:polymer_closure",
        "@io_bazel_rules_closure//closure/library",
    ],
)

closure_js_binary(
    name = "closure_bin",
    # Known issue: Closure compilation not compatible with Polymer behaviors.
    # See: https://github.com/google/closure-compiler/issues/2042
    compilation_level = "WHITESPACE_ONLY",
    defs = [
        "--polymer_pass",
        "--jscomp_off=duplicate",
        "--force_inject_library=es6_runtime",
    ],
    language = "ECMASCRIPT5",
    deps = [":closure_lib"],
)

filegroup(
    name = "top_sources",
    srcs = [
        "favicon.ico",
        "index.html",
    ],
)

filegroup(
    name = "css_sources",
    srcs = glob(["styles/**/*.css"]),
)

filegroup(
    name = "app_sources",
    srcs = [
        "closure_bin.js",
        "gr-app.html",
    ],
)

genrule2(
    name = "polygerrit_ui",
    srcs = [
        "//lib/fonts:sourcecodepro",
        "//lib/js:highlightjs_files",
        ":top_sources",
        ":css_sources",
        ":app_sources",
        # we extract from the zip, but depend on the component for license checking.
        "@webcomponentsjs//:zipfile",
        "//lib/js:webcomponentsjs",
    ],
    outs = ["polygerrit_ui.zip"],
    cmd = " && ".join([
        "mkdir -p $$TMP/polygerrit_ui/{styles,fonts,bower_components/{highlightjs,webcomponentsjs},elements}",
        "for f in $(locations :app_sources); do ext=$${f##*.}; cp -p $$f $$TMP/polygerrit_ui/elements/gr-app.$$ext; done",
        "cp $(locations //lib/fonts:sourcecodepro) $$TMP/polygerrit_ui/fonts/",
        "for f in $(locations :top_sources); do cp $$f $$TMP/polygerrit_ui/; done",
        "for f in $(locations :css_sources); do cp $$f $$TMP/polygerrit_ui/styles; done",
        "for f in $(locations //lib/js:highlightjs_files); do cp $$f $$TMP/polygerrit_ui/bower_components/highlightjs/ ; done",
        "unzip -qd $$TMP/polygerrit_ui/bower_components $(location @webcomponentsjs//:zipfile) webcomponentsjs/webcomponents-lite.js",
        "cd $$TMP",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -qr $$ROOT/$@ *",
    ]),
)

bower_component_bundle(
    name = "test_components",
    testonly = 1,
    deps = [
        "//lib/js:iron-test-helpers",
        "//lib/js:test-fixture",
        "//lib/js:web-component-tester",
        "//polygerrit-ui:polygerrit_components.bower_components",
    ],
)

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
        ],
    ),
)

genrule2(
    name = "pg_code_zip",
    srcs = [":pg_code"],
    outs = ["pg_code.zip"],
    cmd = " && ".join([
        ("tar -hcf- $(locations :pg_code) |" +
         " tar --strip-components=2 -C $$TMP/ -xf-"),
        "cd $$TMP",
        "TZ=UTC",
        "export TZ",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -rq $$ROOT/$@ *",
    ]),
)

sh_test(
    name = "wct_test",
    size = "large",
    srcs = ["wct_test.sh"],
    data = [
        "test/index.html",
        ":pg_code.zip",
        ":test_components.zip",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)
