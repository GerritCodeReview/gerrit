load('//tools/bzl:genrule2.bzl', 'genrule2')
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
DEPS = ['//lib/gwt:user']
SRC = 'src/main/java/com/google/gerrit/'
DIFFY = glob(['src/main/resources/com/google/gerrit/client/diffy*.png'])

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

java_import(
  name = 'diffy_logo',
  jars = [':diffy_image_files_ln'],
  visibility = ['//visibility:public'],
  data = [
    '//lib:LICENSE-diffy',
    '//lib:LICENSE-CC-BY3.0-unported',
  ],
)

genrule2(
  name = 'diffy_image_files_ln',
  srcs = [':diffy_image_files'],
  cmd = 'ln -s $$ROOT/$(location :diffy_image_files) $@',
  out = 'diffy_images.jar',
)

java_library(
  name = 'diffy_image_files',
  resources = DIFFY,
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
