load('//tools/bzl:gwt.bzl', 'gwt_module')

MODULE = 'com.google.gerrit.GerritGwtUI'

gwt_module(
  name = 'ui_module',
  srcs = glob(['src/main/java/**/*.java']),
  gwt_xml = 'src/main/java/%s.gwt.xml' % MODULE.replace('.', '/'),
  deps = [
    '//gerrit-gwtui-common:diffy_logo',
    '//gerrit-gwtui-common:client',
    '//gerrit-gwtexpui:CSS',
    '//lib/codemirror:codemirror',
    '//lib/gwt:user',
  ],
  visibility = ['//visibility:public'],
)
