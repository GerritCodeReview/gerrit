load('//tools/bzl:gwt.bzl', 'gwt_module')

SRC = 'src/main/java/com/google/gerrit/'

ANNOTATIONS = [
  SRC + 'common/Nullable.java',
  SRC + 'common/audit/Audit.java',
  SRC + 'common/auth/SignInRequired.java',
]

java_library(
  name = 'annotations',
  srcs = ANNOTATIONS,
  visibility = ['//visibility:public'],
)

gwt_module(
  name = 'client',
  srcs = glob([SRC + 'common/**/*.java']),
  gwt_xml = SRC + 'Common.gwt.xml',
  deps = [
    '//gerrit-extension-api:api',
    '//gerrit-prettify:client',
    '//lib:guava',
    '//lib:gwtorm_client',
    '//lib:servlet-api-3_1',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/joda:joda-time',
    '//lib/log:api',
  ],
  exports = [
    '//gerrit-extension-api:api',
    '//gerrit-prettify:client',
    '//lib:guava',
    '//lib:gwtorm_client',
    '//lib:servlet-api-3_1',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/joda:joda-time',
    '//lib/log:api',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'server',
  srcs = glob([SRC + 'common/**/*.java'], exclude = ANNOTATIONS),
  deps = [
    ':annotations',
    '//gerrit-extension-api:api',
    '//gerrit-patch-jgit:server',
    '//gerrit-prettify:server',
    '//gerrit-reviewdb:server',
    '//lib:guava',
    '//lib:gwtjsonrpc',
    '//lib:gwtorm',
    '//lib:servlet-api-3_1',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib/joda:joda-time',
    '//lib/log:api',
  ],
  visibility = ['//visibility:public'],
)
