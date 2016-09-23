load('//tools/bzl:gwt.bzl', 'gwt_module')
load('//tools/bzl:genrule2.bzl', 'genrule2')
load(':gwt.bzl', 'gwt_binary', 'gwt_genrule', 'gen_ui_module')

gwt_genrule()
gwt_genrule('_r')

gen_ui_module(name = 'ui_module')
gen_ui_module(name = 'ui_module', suffix = '_r')
