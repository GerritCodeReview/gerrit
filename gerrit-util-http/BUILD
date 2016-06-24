load('//tools/bzl:junit.bzl', 'junit_tests')

java_library(
  name = 'http',
  srcs = glob(['src/main/java/**/*.java']),
  deps = ['//lib:servlet-api-3_1'],
  visibility = ['//visibility:public'],
)

TESTUTIL_SRCS = glob(['src/test/**/testutil/**/*.java'])

java_library(
  name = 'testutil',
  srcs = TESTUTIL_SRCS,
  deps = [
    '//gerrit-extension-api:api',
    '//lib:guava',
    '//lib:servlet-api-3_1',
    '//lib/httpcomponents:httpclient',
    '//lib/jgit/org.eclipse.jgit:jgit',
  ],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'http_tests',
  srcs = glob(
    ['src/test/java/**/*.java'],
    exclude = TESTUTIL_SRCS,
  ),
  deps = [
    ':http',
    ':testutil',
    '//lib:junit',
    '//lib:servlet-api-3_1-without-neverlink',
    '//lib:truth',
    '//lib/easymock:easymock',
  ],
)
