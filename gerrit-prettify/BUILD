load('//tools/bzl:gwt.bzl', 'gwt_module')

SRC = 'src/main/java/com/google/gerrit/prettify/'

gwt_module(
  name = 'client',
  srcs = glob([
    SRC + 'common/**/*.java',
  ]),
  gwt_xml = SRC + 'PrettyFormatter.gwt.xml',
  deps = ['//lib/gwt:user'],
  exported_deps = [
    '//gerrit-extension-api:client',
    '//gerrit-gwtexpui:SafeHtml',
    '//gerrit-patch-jgit:client',
    '//gerrit-patch-jgit:Edit',
    '//gerrit-reviewdb:client',
    '//lib:gwtjsonrpc',
    '//lib:gwtjsonrpc_src',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'server',
  srcs = glob([SRC + 'common/**/*.java']),
  deps = [
    '//gerrit-patch-jgit:server',
    '//gerrit-reviewdb:server',
    '//lib:guava',
    '//lib:gwtjsonrpc',
    '//lib/jgit/org.eclipse.jgit:jgit',
  ],
  visibility = ['//visibility:public'],
)
