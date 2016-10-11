package(
  default_visibility = ["//visibility:public"])
load("//tools/bzl:js.bzl", "bower_component_bundle", "vulcanize")

WCT_TEST_PATTERNS = [
  'test/*.js',
  'test/*.html',
  '**/*_test.html',
]
PY_TEST_PATTERNS = ['polygerrit_wct_tests.py']
APP_SRCS = glob(
  ['**'],
  exclude = [
    'BUCK',
    '*~',
    '**/BUILD',
    'index.html',
    'test/**',
  ] + WCT_TEST_PATTERNS + PY_TEST_PATTERNS)


bower_component_bundle(
  name = 'test_components',
  deps = [
    '//polygerrit-ui:polygerrit_components',
    '//lib/js:iron-test-helpers',
    '//lib/js:test-fixture',
    '//lib/js:web-component-tester',
  ],
)

vulcanize(
  name = "gr-app",
  app = 'elements/gr-app.html',
  srcs = APP_SRCS,
  deps = [ "//polygerrit-ui:polygerrit_components"],
)
