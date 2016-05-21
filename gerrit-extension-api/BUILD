load('//tools/bzl:gwt.bzl', 'gwt_module')

SRC = 'src/main/java/com/google/gerrit/extensions/'
SRCS = glob([SRC + '**/*.java'])

EXT_API_SRCS = glob([SRC + 'client/*.java'])

gwt_module(
  name = 'client',
  srcs = EXT_API_SRCS,
  gwt_xml = SRC + 'Extensions.gwt.xml',
  visibility = ['//visibility:public'],
)

java_binary(
  name = 'extension-api',
  main_class = 'Dummy',
  runtime_deps = [':lib'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'lib',
  exports = [
    ':api',
    '//lib/guice:guice',
    '//lib/guice:guice-assistedinject',
    '//lib/guice:guice-servlet',
    '//lib:servlet-api-3_1',
  ],
  visibility = ['//visibility:public'],
)

#TODO(davido): There is no provided_deps argument to java_library rule
java_library(
  name = 'api',
  srcs = glob([SRC + '**/*.java']),
  deps = [
    '//gerrit-common:annotations',
    '//lib/guice:guice',
    '//lib/guice:guice-assistedinject',
  ],
  visibility = ['//visibility:public'],
)
