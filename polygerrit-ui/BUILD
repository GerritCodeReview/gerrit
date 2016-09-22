
load("//tools/bzl:js.bzl", "bower_component_bundle")
load('//tools/bzl:genrule2.bzl', 'genrule2')

bower_component_bundle(
  name = "components",
  deps = [
    '//lib/js:iron-autogrow-textarea',
])


genrule2(
  name = 'fonts',
  cmd = ' && '.join([
    'cd $TMP',
    'for file in $SRCS; do unzip -q $file; done',
    'zip -q $OUT *',
  ]),
  srcs = [
    '//lib/fonts:sourcecodepro',
  ],
  out = 'fonts.zip',
  visibility = ['PUBLIC'],
)
