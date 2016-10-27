package(
  default_visibility = ["//visibility:public"]
)

load(
  "//tools/bzl:js.bzl",
  "bower_component_bundle", "vulcanize",
  "bower_component", "js_component")
load('//tools/bzl:genrule2.bzl', 'genrule2')

vulcanize(
  name = "gr-app",
  app = 'elements/gr-app.html',
  srcs = glob(
  ['**/*.html', '**/*.js'],
  exclude = [
    'bower_components/**',
    'index.html',
    'test/**',
    '**/*_test.html',
    ]),
  deps = [ "//polygerrit-ui:polygerrit_components"],
)

filegroup(
  name = "top_sources",
  srcs = glob([
    'favicon.ico',
    'index.html',
  ]),
)

filegroup(
  name = "css_sources",
  srcs = glob(['styles/**/*.css'])
)

genrule(
  name = "polygerrit_ui",
  cmd = " && ".join([
    "t=$$(mktemp -d || mktemp -d -t bazel-tmp)",
    "p=$$PWD",
    "mkdir -p $$t/polygerrit_ui/{styles,fonts,bower_components/{highlightjs,webcomponentsjs},elements}",
    "cp $(locations :gr-app) $$t/polygerrit_ui/elements/",
    "cp $(locations //lib/fonts:sourcecodepro) $$t/polygerrit_ui/fonts/",
    "for f in $(locations :top_sources); do cp $$f $$t/polygerrit_ui/; done",
    "for f in $(locations :css_sources); do cp $$f $$t/polygerrit_ui/styles; done",
    "for f in $(locations //lib/js:highlightjs_files); do cp $$f $$t/polygerrit_ui/bower_components/highlightjs/ ; done",
    "unzip -qd $$t/polygerrit_ui/bower_components $(location @webcomponentsjs//:zipfile) webcomponentsjs/webcomponents-lite.js",
    "cd $$t",
    "find . -exec touch -t 198001010000 '{}' ';'",
    "zip -qr $$p/$@ *",
  ]),
  srcs = [
    "//lib/fonts:sourcecodepro",
    "//lib/js:highlightjs_files",
    ":top_sources",
    ":css_sources",
    ":gr-app",
    # we extract from the zip, but depend on the component for license checking.
    "@webcomponentsjs//:zipfile",
    "//lib/js:webcomponentsjs",
  ],
  outs = [ "polygerrit_ui.zip" ],
)

bower_component_bundle(
  name = 'test_components',
  deps = [
    '//polygerrit-ui:polygerrit_components',
    '//lib/js:iron-test-helpers',
    '//lib/js:test-fixture',
    '//lib/js:web-component-tester',
  ],
)

filegroup(
  name = "pg_code",
  srcs = glob([
    '**/*.html',
    '**/*.js',
  ]))

genrule2(
    name = "pg_code_zip",
    out = "pg_code.zip",
    srcs = [ ":pg_code" ],
    cmd = " && ".join([
      ("tar -cf- --mtime=1980-01-01\\ 00:00 $(locations :pg_code) |"
       + " tar --strip-components=2 -C $$TMP/ -xf-"),
      "cd $$TMP",
      "zip -rq $$ROOT/$@ *"]))

sh_test(
  name = "wct_test",
  srcs = [ "wct_test.sh" ],
  data = [
    ":pg_code.zip",
    ":test_components.zip",
    "test/index.html",
  ],
  # Should not run sandboxed.
  tags = ["local"],
)
