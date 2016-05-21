load('//gerrit-acceptance-tests:tests.bzl', 'acceptance_tests')

acceptance_tests(
  group = 'api_group',
  srcs = glob(['*IT.java']),
  deps = [
    ':util',
    '//gerrit-acceptance-tests/src/test/java/com/google/gerrit/acceptance/rest/account:util',
  ],
  labels = ['api'],
)

java_library(
  name = 'util',
  srcs = ['GroupAssert.java'],
  deps = [
    '//gerrit-extension-api:api',
    '//gerrit-reviewdb:server',
    '//gerrit-server:server',
    '//lib:gwtorm',
    '//lib:truth',
  ],
)
