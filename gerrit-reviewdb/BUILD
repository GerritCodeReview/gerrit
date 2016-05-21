load('//tools/bzl:gwt.bzl', 'gwt_module')

SRC = 'src/main/java/com/google/gerrit/reviewdb/'

gwt_module(
  name = 'client',
  srcs = glob([SRC + 'client/**/*.java']),
  gwt_xml = SRC + 'ReviewDB.gwt.xml',
  deps = [
    '//gerrit-extension-api:client',
    '//lib:gwtorm_client',
    '//lib:gwtorm_client_src'
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'server',
  srcs = glob([SRC + '**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  deps = [
    '//gerrit-extension-api:api',
    '//lib:guava',
    '//lib:gwtorm',
  ],
  visibility = ['//visibility:public'],
)
