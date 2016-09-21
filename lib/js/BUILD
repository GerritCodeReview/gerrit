package(default_visibility = [ "//visibility:public" ])
load('//tools/bzl:genrule2.bzl', 'genrule2')
load("//tools/bzl:js.bzl", "bower_component", "js_component")

load("//lib/js:bower_components.bzl", "define_bower_components")
define_bower_components()


# Zip highlightjs so that it can be imported as though it were a
# bower_component and also attach the library license to the Buck dependency
# graph.
js_component(
  name = 'highlightjs',
  srcs = [ "//lib/highlightjs:highlight.min.js" ],
#  license = 'highlightjs',
)
