load('//tools/bzl:java.bzl', 'java_library2')
load('//tools/bzl:junit.bzl', 'junit_tests')
load('//tools/bzl:gwt.bzl', 'gwt_module')

EXPORTED_DEPS = [
  '//gerrit-common:client',
  '//gerrit-gwtexpui:Clippy',
  '//gerrit-gwtexpui:GlobalKey',
  '//gerrit-gwtexpui:Progress',
  '//gerrit-gwtexpui:SafeHtml',
  '//gerrit-gwtexpui:UserAgent',
]
DEPS = ['//lib/gwt:user-neverlink']
SRC = 'src/main/java/com/google/gerrit/'

gwt_module(
  name = 'client',
  srcs = glob(['src/main/**/*.java']),
  gwt_xml = SRC + 'GerritGwtUICommon.gwt.xml',
  resources = glob(
      ['src/main/**/*'],
      exclude = [SRC + 'client/**/*.java'] +
      [SRC + 'GerritGwtUICommon.gwt.xml']
  ),
  exported_deps = EXPORTED_DEPS,
  deps = DEPS,
  visibility = ['//visibility:public'],
)

java_library2(
  name = 'client-lib',
  srcs = glob(['src/main/**/*.java']),
  resources = glob(['src/main/**/*']),
  exported_deps = EXPORTED_DEPS,
  deps = DEPS,
  visibility = ['//visibility:public'],
)

java_library(
  name = 'diffy_logo',
  resources = glob(['src/main/resources/com/google/gerrit/client/diffy*.png']),
  data = [
    '//lib:LICENSE-diffy',
    '//lib:LICENSE-CC-BY3.0-unported',
  ],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'client_tests',
  srcs = glob(['src/test/java/**/*.java']),
  deps = [
    ':client',
    '//lib:junit',
    '//lib/gwt:dev',
    '//lib/jgit/org.eclipse.jgit:jgit',
  ],
  visibility = ['//visibility:public'],
)
