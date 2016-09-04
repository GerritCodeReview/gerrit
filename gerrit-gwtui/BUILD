load('//tools/bzl:gwt.bzl', 'gwt_module')
load('//tools/bzl:genrule2.bzl', 'genrule2')
load(':gwt.bzl', 'gwt_binary')

MODULE = 'com.google.gerrit.GerritGwtUI'

GWT_JVM_ARGS = ['-Xmx512m']

GWT_COMPILER_ARGS = [
  '-XdisableClassMetadata',
]

GWT_COMPILER_ARGS_RELEASE_MODE = GWT_COMPILER_ARGS + [
  '-XdisableCastChecking',
]

GWT_TRANSITIVE_DEPS = [
  '//lib/gwt:javax-validation',
  '//lib/gwt:javax-validation_src',
  '//lib/ow2:ow2-asm',
  '//lib/ow2:ow2-asm-analysis',
  '//lib/ow2:ow2-asm-commons',
  '//lib/ow2:ow2-asm-tree',
  '//lib/ow2:ow2-asm-util',
]

DEPS = GWT_TRANSITIVE_DEPS + [
  '//gerrit-gwtexpui:CSS',
  '//lib:gwtjsonrpc',
  '//lib/gwt:dev',
  # TODO(davido): Why is this needed to be specified here
  # and is not reached through transitive dependency chain?
  '@jgit_src//file',
]

gwt_module(
  name = 'ui_module',
  srcs = glob(['src/main/java/**/*.java']),
  gwt_xml = 'src/main/java/%s.gwt.xml' % MODULE.replace('.', '/'),
  resources = glob(
      ['src/main/java/**/*'],
      exclude = ['src/main/java/**/*.java'] +
      ['src/main/java/%s.gwt.xml' % MODULE.replace('.', '/')]
  ),
  deps = [
    '//gerrit-gwtui-common:diffy_logo',
    '//gerrit-gwtui-common:client',
    '//gerrit-gwtexpui:CSS',
    '//lib/codemirror:codemirror',
    '//lib/gwt:user',
  ],
  visibility = ['//visibility:public'],
)

genrule2(
  name = 'ui_optdbg',
  srcs = [
    ':ui_dbg',
    ':ui_opt',
  ],
  cmd = 'cd $$TMP;' +
    'unzip -q $$ROOT/$(location :ui_dbg);' +
    'mv' +
    ' gerrit_ui/gerrit_ui.nocache.js' +
    ' gerrit_ui/dbg_gerrit_ui.nocache.js;' +
    'unzip -qo $$ROOT/$(location :ui_opt);' +
    'mkdir -p $$(dirname $@);' +
    'zip -qr $$ROOT/$@ .',
  out = 'ui_optdbg.zip',
  visibility = ['//visibility:public'],
)

gwt_binary(
  name = 'ui_opt',
  modules = [MODULE],
  module_deps = [':ui_module'],
  deps = DEPS,
  compiler_args = GWT_COMPILER_ARGS,
  jvm_args = GWT_JVM_ARGS,
)

gwt_binary(
  name = 'ui_dbg',
  modules = [MODULE],
  style = 'PRETTY',
  optimize = "0",
  module_deps = [':ui_module'],
  deps = DEPS,
  compiler_args = GWT_COMPILER_ARGS,
  jvm_args = GWT_JVM_ARGS,
)
